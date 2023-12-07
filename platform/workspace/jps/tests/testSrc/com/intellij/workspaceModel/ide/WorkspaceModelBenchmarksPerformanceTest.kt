package com.intellij.workspaceModel.ide

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.JpsProjectConfigLocation
import com.intellij.platform.workspace.jps.OrphanageWorkerEntitySource
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.jps.serialization.impl.ErrorReporter
import com.intellij.platform.workspace.jps.serialization.impl.JpsProjectEntitiesLoader
import com.intellij.platform.workspace.jps.serialization.impl.JpsProjectSerializers
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.serialization.EntityStorageSerializerImpl
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.platform.workspace.storage.testEntities.entities.*
import com.intellij.platform.workspace.storage.toBuilder
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.utils.io.createDirectory
import com.intellij.testFramework.workspaceModel.updateProjectModel
import com.intellij.workspaceModel.ide.impl.IdeVirtualFileUrlManagerImpl
import com.intellij.workspaceModel.ide.impl.WorkspaceModelCacheSerializer.PluginAwareEntityTypesResolver
import com.intellij.workspaceModel.ide.impl.jps.serialization.CachingJpsFileContentReader
import com.intellij.workspaceModel.ide.impl.jps.serialization.SerializationContextForTests
import junit.framework.AssertionFailedError
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.runBlocking
import org.jetbrains.jps.model.serialization.PathMacroUtil
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.measureTimeMillis


@TestApplication
class WorkspaceModelBenchmarksPerformanceTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  @TempDir
  lateinit var tempFolder: Path

  private fun Path.newRandomDirectory(): Path = this.createDirectory("random_directory_name".asSequence().shuffled().toString())


  @BeforeEach
  fun beforeTest() {
    println("> Benchmark test started")
    Registry.get(EntitiesOrphanage.orphanageKey).setValue(true)
  }

  @AfterEach
  fun afterTest() {
    println("> Benchmark test finished")
    Registry.get(EntitiesOrphanage.orphanageKey).setValue(false)
  }

  @Test
  fun addingStorageRecreating(testInfo: TestInfo) {

    var storage = MutableEntityStorage.create().toSnapshot()
    val times = 20_000

    PlatformTestUtil.startPerformanceTest(testInfo.displayName, 100500) {
      repeat(times) {
        val builder = storage.toBuilder()


        val ooParent = builder addEntity NamedEntity("$it", MySource)
        builder addEntity NamedChildEntity("Child", MySource) {
          this.parentEntity = ooParent
        }
        builder addEntity ComposedIdSoftRefEntity("-$it", ooParent.symbolicId, MySource)

        storage = builder.toSnapshot()
      }
    }
      .warmupIterations(0)
      .attempts(1).assertTiming()
  }

  @Test
  fun requestingSameEntity(testInfo: TestInfo) {
    val storage = MutableEntityStorage.create().also { builder -> builder addEntity NamedEntity("data", MySource) }.toSnapshot()
    val blackhole: (WorkspaceEntity) -> Unit = { }

    val times = 2_000_000

    PlatformTestUtil.startPerformanceTest(testInfo.displayName, 100500) {
      repeat(times) {
        val entity = storage.entities(NamedEntity::class.java).single()
        blackhole(entity)
        if (it % 1_000 == 0) {
          System.gc()
        }
      }
    }
      .warmupIterations(0)
      .attempts(1).assertTiming()
  }

  @Test
  fun addingSoftLinkedEntities(testInfo: TestInfo) {

    val builder = MutableEntityStorage.create()
    val times = 2_000_000
    val parents = ArrayList<NamedEntity>(times)

    PlatformTestUtil.startPerformanceTest("Named entities adding", 100500) {
      repeat(times) {
        parents += builder addEntity NamedEntity("$it", MySource)
      }
    }
      .warmupIterations(0)
      .attempts(1).assertTimingAsSubtest()

    PlatformTestUtil.startPerformanceTest("Soft linked entities adding", 100500) {
      for (parent in parents) {
        builder addEntity ComposedIdSoftRefEntity("-${parent.myName}", parent.symbolicId, MySource)
      }
    }
      .warmupIterations(0)
      .attempts(1).assertTimingAsSubtest()
  }

  @Test
  fun renamingNamedEntities(testInfo: TestInfo) {
    val builder: MutableEntityStorage = MutableEntityStorage.create()
    val size = 2_000_000

    repeat(size) {
      val namedEntity = builder addEntity NamedEntity("$it", MySource)
      builder addEntity ComposedIdSoftRefEntity("-$it", namedEntity.symbolicId, MySource)
    }
    val storage = builder.toSnapshot()
    val newBuilder = storage.toBuilder()

    PlatformTestUtil.startPerformanceTest(testInfo.displayName, 100500) {
      repeat(size) {
        val value = newBuilder.resolve(NameId("$it"))!!
        newBuilder.modifyEntity(value) {
          myName = "--- $it ---"
        }
      }
    }
      .warmupIterations(0)
      .attempts(1).assertTiming()
  }

  @Test
  fun refersNamedEntities(testInfo: TestInfo) {
    val builder: MutableEntityStorage = MutableEntityStorage.create()
    val size = 3_000_000

    repeat(size) {
      val namedEntity = builder.addNamedEntity("$it")
      builder.addComposedIdSoftRefEntity("-$it", namedEntity.symbolicId)
    }

    val storage = builder.toSnapshot()
    val list = mutableListOf<ComposedIdSoftRefEntity>()

    PlatformTestUtil.startPerformanceTest(testInfo.displayName, 100500) {
      repeat(size) {
        list.addAll(storage.referrers(NameId("$it"), ComposedIdSoftRefEntity::class.java).toList())
      }
    }
      .warmupIterations(0)
      .attempts(1).assertTiming()
  }

  @Test
  fun serializeCommunityProject(testInfo: TestInfo) {
    val storageBuilder = MutableEntityStorage.create()
    val projectDir = File(PathManagerEx.getCommunityHomePath())
    val manager = IdeVirtualFileUrlManagerImpl()
    runBlocking {
      loadProject(projectDir.asConfigLocation(manager), storageBuilder, manager)
    }

    val storage = storageBuilder.toSnapshot()
    val serializer = EntityStorageSerializerImpl(PluginAwareEntityTypesResolver, manager)

    val sizes = ArrayList<Int>()

    val file = Files.createTempFile("tmpModel", "")
    try {
      PlatformTestUtil.startPerformanceTest("${testInfo.displayName} - Serialization", 100500) {
        repeat(200) {
          serializer.serializeCache(file, storage)
        }
      }
        .warmupIterations(0)
        .attempts(1).assertTimingAsSubtest()

      PlatformTestUtil.startPerformanceTest("${testInfo.displayName} - Deserialization", 100500) {
        repeat(200) {
          sizes += Files.size(file).toInt()
          serializer.deserializeCache(file).getOrThrow()
        }
      }
        .warmupIterations(0)
        .attempts(1).assertTimingAsSubtest()

      PlatformTestUtil.startPerformanceTest("${testInfo.displayName} - SerializationFromFile", 100500) {
        repeat(200) {
          serializer.serializeCache(file, storage)
        }
      }
        .warmupIterations(0)
        .attempts(1).assertTimingAsSubtest()

      PlatformTestUtil.startPerformanceTest("${testInfo.displayName} - DeserializationFromFile", 100500) {
        repeat(200) {
          serializer.deserializeCache(file).getOrThrow()
        }
      }
        .warmupIterations(0)
        .attempts(1).assertTimingAsSubtest()
    }
    finally {
      Files.deleteIfExists(file)
    }
  }

  @Test
  fun rbsNewOnManyContentRoots(testInfo: TestInfo) {
    val manager = IdeVirtualFileUrlManagerImpl()
    val newFolder = tempFolder.newRandomDirectory()

    val storageBuilder = MutableEntityStorage.create()
    val module = ModuleEntity("data", emptyList(), MySource)
    storageBuilder.addEntity(module)
    repeat(1_000) {
      storageBuilder.addEntity(ContentRootEntity(manager.fromPath("$newFolder/url${it}"), emptyList(), MySource) {
        this.module = module
      })
    }

    val replaceStorage = MutableEntityStorage.create()
    val replaceModule = ModuleEntity("data", emptyList(), MySource)
    replaceStorage.addEntity(replaceModule)
    repeat(1_000) {
      replaceStorage.addEntity(ContentRootEntity(manager.fromPath("$newFolder/url${it}"), emptyList(), MySource) {
        this.module = replaceModule
      })
    }

    PlatformTestUtil.startPerformanceTest(testInfo.displayName, 100500) {
      storageBuilder.replaceBySource({ true }, replaceStorage)
    }
      .warmupIterations(0)
      .attempts(1).assertTiming()
  }

  @Test
  fun `project model updates`(testInfo: TestInfo) {
    PlatformTestUtil.startPerformanceTest(testInfo.displayName, 100500) {
      runWriteActionAndWait {
        measureTimeMillis {
          repeat(10_000) {
            WorkspaceModel.getInstance(projectModel.project).updateProjectModel {
              it addEntity LeftEntity(MySource)
            }
          }
        }
      }
    }
      .warmupIterations(0)
      .attempts(1).assertTiming()
  }

  @Test
  fun `10_000 orphan content roots to modules`(testInfo: TestInfo) {
    val manager = VirtualFileUrlManager.getInstance(projectModel.project)
    val newFolder = tempFolder.newRandomDirectory()

    PlatformTestUtil.startPerformanceTest(testInfo.displayName, 100500) {
      runWriteActionAndWait {
        measureTimeMillis {
          EntitiesOrphanage.getInstance(projectModel.project).update {
            repeat(10_000) { counter ->
              it addEntity ModuleEntity("Module$counter", emptyList(), OrphanageWorkerEntitySource) {
                contentRoots = listOf(ContentRootEntity(manager.fromPath("$newFolder/data$counter"), emptyList(), MySource))
              }
            }
          }

          WorkspaceModel.getInstance(projectModel.project).updateProjectModel {
            repeat(10_000) { counter ->
              it addEntity ModuleEntity("Module$counter", emptyList(), MySource)
            }
          }
        }
      }
    }
      .warmupIterations(0)
      .attempts(1).assertTiming()

    ApplicationManager.getApplication().invokeAndWait {
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }

    projectModel.project.workspaceModel.currentSnapshot.entities(ModuleEntity::class.java).forEach {
      Assertions.assertTrue(it.contentRoots.isNotEmpty())
    }
  }

  @Test
  fun `10_000 orphan source roots to modules`(testInfo: TestInfo) {
    val newFolder = tempFolder.newRandomDirectory()
    val manager = VirtualFileUrlManager.getInstance(projectModel.project)

    PlatformTestUtil.startPerformanceTest(testInfo.displayName, 100500) {
      runWriteActionAndWait {
        measureTimeMillis {
          EntitiesOrphanage.getInstance(projectModel.project).update {
            repeat(10_000) { counter ->
              it addEntity ModuleEntity("Module$counter", emptyList(), OrphanageWorkerEntitySource) {
                contentRoots = listOf(
                  ContentRootEntity(manager.fromPath("$newFolder/data$counter"), emptyList(), OrphanageWorkerEntitySource) {
                    this.sourceRoots = listOf(
                      SourceRootEntity(manager.fromPath("$newFolder/one$counter"), "", MySource),
                      SourceRootEntity(manager.fromPath("$newFolder/two$counter"), "", MySource),
                      SourceRootEntity(manager.fromPath("$newFolder/three$counter"), "", MySource),
                      SourceRootEntity(manager.fromPath("$newFolder/four$counter"), "", MySource),
                      SourceRootEntity(manager.fromPath("$newFolder/five$counter"), "", MySource),
                    )
                  })
              }
            }
          }

          WorkspaceModel.getInstance(projectModel.project).updateProjectModel {
            repeat(10_000) { counter ->
              it addEntity ModuleEntity("Module$counter", emptyList(), MySource) {
                contentRoots = listOf(ContentRootEntity(manager.fromPath("$newFolder/data$counter"), emptyList(), MySource))
              }
            }
          }
        }
      }
    }
      .warmupIterations(0)
      .attempts(1).assertTiming()

    ApplicationManager.getApplication().invokeAndWait {
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }

    projectModel.project.workspaceModel.currentSnapshot.entities(ModuleEntity::class.java).forEach {
      Assertions.assertTrue(it.contentRoots.isNotEmpty() && it.contentRoots.all { it.sourceRoots.size == 5 })
    }
  }

  @Test
  fun `10_000 orphan source roots to many content roots to modules`(testInfo: TestInfo) {
    val newFolder = tempFolder.newRandomDirectory()
    val manager = VirtualFileUrlManager.getInstance(projectModel.project)

    PlatformTestUtil.startPerformanceTest(testInfo.displayName, 100500) {
      runWriteActionAndWait {
        measureTimeMillis {
          EntitiesOrphanage.getInstance(projectModel.project).update {
            repeat(10_000) { counter ->
              it addEntity ModuleEntity("Module$counter", emptyList(), OrphanageWorkerEntitySource) {
                contentRoots = List(10) { contentCounter ->
                  ContentRootEntity(manager.fromPath("$newFolder/data$contentCounter$counter"), emptyList(), OrphanageWorkerEntitySource) {
                    sourceRoots = List(10) { sourceCounter ->
                      SourceRootEntity(manager.fromPath("$newFolder/one$sourceCounter$contentCounter$counter"), "", MySource)
                    }
                  }
                }
              }
            }
          }

          WorkspaceModel.getInstance(projectModel.project).updateProjectModel {
            repeat(10_000) { counter ->
              it addEntity ModuleEntity("Module$counter", emptyList(), MySource) {
                contentRoots = List(10) { contentCounter ->
                  ContentRootEntity(manager.fromPath("$newFolder/data$contentCounter$counter"), emptyList(), OrphanageWorkerEntitySource)
                }
              }
            }
          }
        }
      }
    }
      .warmupIterations(0)
      .attempts(1).assertTiming()

    ApplicationManager.getApplication().invokeAndWait {
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }

    projectModel.project.workspaceModel.currentSnapshot.entities(ModuleEntity::class.java).forEach {
      Assertions.assertTrue(it.contentRoots.isNotEmpty() && it.contentRoots.all { it.sourceRoots.size == 10 })
    }
  }

  @Test
  fun `update storage via replaceProjectModel`(testInfo: TestInfo) {
    PlatformTestUtil.startPerformanceTest(testInfo.displayName, 100500) {
      runWriteActionAndWait {
        repeat(1000) {
          val builderSnapshot = WorkspaceModel.getInstance(projectModel.project).getBuilderSnapshot()
          builderSnapshot.builder addEntity ModuleEntity("Module$it", emptyList(), MySource)
          WorkspaceModel.getInstance(projectModel.project).replaceProjectModel(builderSnapshot.getStorageReplacement())
        }
      }
    }
      .warmupIterations(0)
      .attempts(1).assertTiming()

    // TODO: second part of the tests need to be implemented later "applyStorageTime" - variable
    //  (or unit perf metrics publishing should be able to read meters from CSV (where we may store counters)
    // var applyStorageTime = 0L
    //    val duration = measureTimeMillis {
    //      runWriteActionAndWait {
    //        repeat(1000) {
    //          val builderSnapshot = WorkspaceModel.getInstance(projectModel.project).getBuilderSnapshot()
    //          builderSnapshot.builder addEntity ModuleEntity("Module$it", emptyList(), MySource)
    //          applyStorageTime += measureTimeMillis {
    //            WorkspaceModel.getInstance(projectModel.project).replaceProjectModel(builderSnapshot.getStorageReplacement())
    //          }
    //        }
    //      }
    //    }
    //
    //    val metrics = mapOf("duration" to duration, "duration_replace" to applyStorageTime)
  }

  @Test
  fun `collect changes`(testInfo: TestInfo) {
    val builders = List(1000) {
      val builder = MutableEntityStorage.create()

      // Set initial state
      repeat(1000) {
        builder addEntity NamedEntity("MyName$it", MySource)
      }
      repeat(1000) {
        builder addEntity ParentEntity("data$it", MySource) {
          this.child = ChildEntity("info$it", MySource)
        }
      }
      repeat(1000) {
        OoParentEntity("prop$it", MySource) {
          this.anotherChild = OoChildWithNullableParentEntity(MySource)
        }
      }

      // Populate builder with changes
      builder.toSnapshot().toBuilder().also { mutable ->
        repeat(1000) {
          val namedEntity = mutable.resolve(NameId("MyName$it"))!!
          mutable.modifyEntity(namedEntity) {
            this.myName = "newName$it"
          }
        }
        repeat(1000) {
          mutable addEntity NamedEntity("Hey$it", MySource)
        }
        mutable.entities(ChildEntity::class.java).forEach { mutable.removeEntity(it) }
        mutable.entities(OoChildWithNullableParentEntity::class.java).forEach {
          mutable.modifyEntity(it) {
            this.parentEntity = null
          }
        }
      }
    }

    PlatformTestUtil.startPerformanceTest(testInfo.displayName, 100500) {
      builders.forEach { it.collectChanges() }
    }
      .warmupIterations(0)
      .attempts(1).assertTiming()
  }

  @Test
  fun `addDiff operation`(testInfo: TestInfo) {
    val builders = List(1000) {
      val builder = MutableEntityStorage.create()

      // Set initial state
      repeat(1000) {
        builder addEntity NamedEntity("MyName$it", MySource)
      }
      repeat(1000) {
        builder addEntity ParentEntity("data$it", MySource) {
          this.child = ChildEntity("info$it", MySource)
        }
      }
      repeat(1000) {
        OoParentEntity("prop$it", MySource) {
          this.anotherChild = OoChildWithNullableParentEntity(MySource)
        }
      }
      builder
    }

    val newBuilders = builders.map { builder ->
      // Populate builder with changes
      builder.toSnapshot().toBuilder().also { mutable ->
        repeat(1000) {
          val namedEntity = mutable.resolve(NameId("MyName$it"))!!
          mutable.modifyEntity(namedEntity) {
            this.myName = "newName$it"
          }
        }
        repeat(1000) {
          mutable addEntity NamedEntity("Hey$it", MySource)
        }
        mutable.entities(ChildEntity::class.java).forEach { mutable.removeEntity(it) }
        mutable.entities(OoChildWithNullableParentEntity::class.java).forEach {
          mutable.modifyEntity(it) {
            this.parentEntity = null
          }
        }
      }
    }

    PlatformTestUtil.startPerformanceTest(testInfo.displayName, 100500) {
      builders.zip(newBuilders).forEach { (initial, update) ->
        initial.addDiff(update)
      }
    }
      .warmupIterations(0)
      .attempts(1).assertTiming()
  }

  @Test
  fun `operations of references`(testInfo: TestInfo) {
    val builders = List(1000) {
      val builder = MutableEntityStorage.create()

      // Set initial state
      repeat(1000) {
        builder addEntity NamedEntity("MyName$it", MySource)
      }
      repeat(1000) {
        builder addEntity ParentEntity("data$it", MySource) {
          this.child = ChildEntity("info$it", MySource)
        }
      }
      repeat(1000) {
        OoParentEntity("prop$it", MySource) {
          this.anotherChild = OoChildWithNullableParentEntity(MySource)
        }
      }
      builder.toSnapshot().toBuilder()
    }

    PlatformTestUtil.startPerformanceTest(testInfo.displayName, 100500) {
      builders.forEach { builder ->
        // Populate builder with changes
        repeat(1000) {
          val namedEntity = builder.resolve(NameId("MyName$it"))!!
          builder.modifyEntity(namedEntity) {
            this.children = listOf(NamedChildEntity("prop", MySource))
          }
        }
        builder.entities(ChildEntity::class.java).forEach { builder.removeEntity(it) }
        builder.entities(OoChildWithNullableParentEntity::class.java).forEach {
          builder.modifyEntity(it) {
            this.parentEntity = null
          }
        }
      }
    }
      .warmupIterations(0)
      .attempts(1).assertTiming()
  }

  @Test
  fun `operations on external mappings`(testInfo: TestInfo) {
    val size = 1_000_000
    val builders = List(size) {
      val mutableEntityStorage = MutableEntityStorage.create()
      mutableEntityStorage addEntity NamedEntity("MyEntity", MySource)
      mutableEntityStorage
    }
    val singleBuilder = MutableEntityStorage.create().also { builder ->
      repeat(size) {
        builder addEntity NamedEntity("MyEntity$it", MySource)
      }
    }

    val buildersToEntity = builders.map { it to it.resolve(NameId("MyEntity"))!! }
    val singleBuilderEntities = singleBuilder.entities(NamedEntity::class.java).map { singleBuilder to it }.toList()

    // Measure adding mappings
    measureOperation("addMapping", singleBuilderEntities, buildersToEntity) { index, (builder, entity) ->
      builder.getMutableExternalMapping<String>("test").addMapping(entity, "data$index")
    }

    measureOperation("getEntity", singleBuilderEntities, buildersToEntity) { index, (builder, _) ->
      builder.getExternalMapping<String>("test").getEntities("data$index")
    }

    measureOperation("getData", singleBuilderEntities, buildersToEntity) { _, (builder, entity) ->
      builder.getExternalMapping<String>("test").getDataByEntity(entity)
    }

    // Measure removal
    measureOperation("removeMapping", singleBuilderEntities, buildersToEntity) { _, (builder, entity) ->
      builder.getMutableExternalMapping<String>("test").removeMapping(entity)
    }
  }

  @Test
  fun `operations on external mappings - update builders in chain`(testInfo: TestInfo) {
    val builder = MutableEntityStorage.create()

    val size = 100_000
    repeat(size) {
      builder addEntity NamedEntity("Name$it", MySource)
    }

    var mySnapshot = builder.toSnapshot()

    PlatformTestUtil.startPerformanceTest(testInfo.displayName, 100500) {
      repeat(size) {
        val myBuilder = mySnapshot.toBuilder()
        val entity = myBuilder.resolve(NameId("Name$it"))!!
        myBuilder.getMutableExternalMapping<String>("test").addMapping(entity, "data$it")
        mySnapshot = myBuilder.toSnapshot()
      }
    }
      .warmupIterations(0)
      .attempts(1).assertTiming()
  }

  @ParameterizedTest
  @ValueSource(ints = [10, 1_000, 100_000, 10_000_000])
  fun `get for kotlin persistent map`(size: Int) {
    val requestSize = 10_000_000

    PlatformTestUtil.startPerformanceTest(size.toString(), 100500) {
      testPersistentMap(size, requestSize)
    }
      .warmupIterations(0)
      .attempts(1).assertTimingAsSubtest()
  }

  @Suppress("SameParameterValue")
  private fun testPersistentMap(size: Int, requestSize: Int): Long {
    val myMap = persistentMapOf<Int, Int>().mutate { map ->
      repeat(size) {
        map[it] = it
      }
    }

    // IDK if this will help at all, but I'd like the compiler not to remove call to map
    val blackhole = List<Int?>(size) { null }.toMutableList()
    return measureTimeMillis {
      repeat(requestSize) {
        val res = myMap[it % size]
        blackhole[it % size] = res
      }
    }
  }

  private fun measureOperation(launchName: String, singleBuilderEntities: List<Pair<MutableEntityStorage, NamedEntity>>,
                               perBuilderEntities: List<Pair<MutableEntityStorage, NamedEntity>>,
                               operation: (Int, Pair<MutableEntityStorage, NamedEntity>) -> Unit): Unit {
    PlatformTestUtil.startPerformanceTest("$launchName-singleBuilderEntities", 100500) {
      singleBuilderEntities.forEachIndexed(operation)
    }
      .warmupIterations(0)
      .attempts(1).assertTimingAsSubtest()

    PlatformTestUtil.startPerformanceTest("$launchName-perBuilderEntities", 100500) {
      perBuilderEntities.forEachIndexed(operation)
    }
      .warmupIterations(0)
      .attempts(1).assertTimingAsSubtest()
  }


  private suspend fun loadProject(configLocation: JpsProjectConfigLocation,
                                  originalBuilder: MutableEntityStorage,
                                  virtualFileManager: VirtualFileUrlManager): JpsProjectSerializers {
    val cacheDirUrl = configLocation.baseDirectoryUrl.append("cache")
    val context = SerializationContextForTests(virtualFileManager, CachingJpsFileContentReader(configLocation))
    return JpsProjectEntitiesLoader.loadProject(configLocation = configLocation,
                                                builder = originalBuilder,
                                                orphanage = originalBuilder,
                                                externalStoragePath = File(VfsUtil.urlToPath(cacheDirUrl.url)).toPath(),
                                                errorReporter = TestErrorReporter,
                                                context = context)
  }

  private fun File.asConfigLocation(virtualFileManager: VirtualFileUrlManager): JpsProjectConfigLocation = toConfigLocation(toPath(),
                                                                                                                            virtualFileManager)

  private fun toConfigLocation(file: Path, virtualFileManager: VirtualFileUrlManager): JpsProjectConfigLocation {
    if (FileUtil.extensionEquals(file.fileName.toString(), "ipr")) {
      val iprFile = file.toVirtualFileUrl(virtualFileManager)
      return JpsProjectConfigLocation.FileBased(iprFile, virtualFileManager.getParentVirtualUrl(iprFile)!!)
    }
    else {
      val projectDir = file.toVirtualFileUrl(virtualFileManager)
      return JpsProjectConfigLocation.DirectoryBased(projectDir, projectDir.append(PathMacroUtil.DIRECTORY_STORE_NAME))
    }
  }


  internal object TestErrorReporter : ErrorReporter {
    override fun reportError(message: String, file: VirtualFileUrl) {
      throw AssertionFailedError("Failed to load ${file.url}: $message")
    }
  }
}
