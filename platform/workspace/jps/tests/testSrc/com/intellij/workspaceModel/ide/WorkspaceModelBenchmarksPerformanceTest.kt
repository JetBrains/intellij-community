// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.components.service
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.impl.WorkspaceModelInternal
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.JpsProjectConfigLocation
import com.intellij.platform.workspace.jps.OrphanageWorkerEntitySource
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.jps.serialization.impl.ErrorReporter
import com.intellij.platform.workspace.jps.serialization.impl.JpsProjectEntitiesLoader
import com.intellij.platform.workspace.jps.serialization.impl.JpsProjectSerializers
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.cache.CacheResetTracker
import com.intellij.platform.workspace.storage.impl.cache.ChangeOnVersionedChange
import com.intellij.platform.workspace.storage.impl.cache.TracedSnapshotCache
import com.intellij.platform.workspace.storage.impl.cache.cache
import com.intellij.platform.workspace.storage.impl.serialization.EntityStorageSerializerImpl
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.ImmutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.query.entities
import com.intellij.platform.workspace.storage.query.flatMap
import com.intellij.platform.workspace.storage.query.groupBy
import com.intellij.platform.workspace.storage.query.map
import com.intellij.platform.workspace.storage.testEntities.entities.*
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.junit5.StressTestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.utils.io.createDirectory
import com.intellij.testFramework.workspaceModel.updateProjectModel
import com.intellij.tools.ide.metrics.benchmark.Benchmark
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
import kotlin.time.Duration
import kotlin.time.measureTime


@StressTestApplication
class WorkspaceModelBenchmarksPerformanceTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  @TempDir
  lateinit var tempFolder: Path

  private fun Path.newRandomDirectory(): Path = this.createDirectory("random_directory_name".asSequence().shuffled().toString())

  private val externalMappingKey = ExternalMappingKey.create<Any>("test")

  @BeforeEach
  fun beforeTest() {
    Assumptions.assumeTrue(UsefulTestCase.IS_UNDER_TEAMCITY, "Skip slow test on local run")
    println("> Benchmark test started")
  }

  @AfterEach
  fun afterTest() {
    println("> Benchmark test finished")
    TracedSnapshotCache.LOG_QUEUE_MAX_SIZE = 10_000
  }

  @Test
  fun addingStorageRecreating(testInfo: TestInfo) {

    var storage = MutableEntityStorage.create().toSnapshot()
    val times = 20_000

    Benchmark.newBenchmark(testInfo.displayName) {
      repeat(times) {
        val builder = storage.toBuilder()


        builder addEntity NamedChildEntity("Child", MySource) {
          this.parentEntity = NamedEntity("$it", MySource)
        }
        builder addEntity ComposedIdSoftRefEntity("-$it", NameId("$it"), MySource)

        storage = builder.toSnapshot()
      }
    }
      .warmupIterations(0)
      .attempts(1).start()
  }

  @Test
  fun requestingSameEntity(testInfo: TestInfo) {
    val storage = MutableEntityStorage.create().also { builder -> builder addEntity NamedEntity("data", MySource) }.toSnapshot()
    val blackhole: (WorkspaceEntity) -> Unit = { }

    val times = 2_000_000

    Benchmark.newBenchmark(testInfo.displayName) {
      repeat(times) {
        val entity = storage.entities(NamedEntity::class.java).single()
        blackhole(entity)
        if (it % 1_000 == 0) {
          System.gc()
        }
      }
    }
      .warmupIterations(0)
      .attempts(1).start()
  }

  @Test
  fun addingSoftLinkedEntities(testInfo: TestInfo) {

    val builder = MutableEntityStorage.create()
    val times = 2_000_000
    val parents = ArrayList<NamedEntity>(times)

    Benchmark.newBenchmark("Named entities adding") {
      repeat(times) {
        parents += builder addEntity NamedEntity("$it", MySource)
      }
    }
      .warmupIterations(0)
      .attempts(1).startAsSubtest()

    Benchmark.newBenchmark("Soft linked entities adding") {
      for (parent in parents) {
        builder addEntity ComposedIdSoftRefEntity("-${parent.myName}", parent.symbolicId, MySource)
      }
    }
      .warmupIterations(0)
      .attempts(1).startAsSubtest()
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

    Benchmark.newBenchmark(testInfo.displayName) {
      repeat(size) {
        val value = newBuilder.resolve(NameId("$it"))!!
        newBuilder.modifyNamedEntity(value) {
          myName = "--- $it ---"
        }
      }
    }
      .warmupIterations(0)
      .attempts(1).start()
  }

  @Test
  fun refersNamedEntities(testInfo: TestInfo) {
    val builder: MutableEntityStorage = MutableEntityStorage.create()
    val size = 3_000_000

    repeat(size) {
      val namedEntity = builder addEntity NamedEntity("$it", MySource)
      builder addEntity ComposedIdSoftRefEntity("-$it", namedEntity.symbolicId, MySource)
    }

    val storage = builder.toSnapshot()
    val list = mutableListOf<ComposedIdSoftRefEntity>()

    Benchmark.newBenchmark(testInfo.displayName) {
      repeat(size) {
        list.addAll(storage.referrers(NameId("$it"), ComposedIdSoftRefEntity::class.java).toList())
      }
    }
      .warmupIterations(0)
      .attempts(1).start()
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
    val serializer = EntityStorageSerializerImpl(PluginAwareEntityTypesResolver, manager, ijBuildVersion = "")

    val sizes = ArrayList<Int>()

    val file = Files.createTempFile("tmpModel", "")
    try {
      Benchmark.newBenchmark("${testInfo.displayName} - Serialization") {
        repeat(200) {
          serializer.serializeCache(file, storage)
        }
      }
        .warmupIterations(0)
        .attempts(1).startAsSubtest()

      Benchmark.newBenchmark("${testInfo.displayName} - Deserialization") {
        repeat(200) {
          sizes += Files.size(file).toInt()
          serializer.deserializeCache(file).getOrThrow()
        }
      }
        .warmupIterations(0)
        .attempts(1).startAsSubtest()

      Benchmark.newBenchmark("${testInfo.displayName} - SerializationFromFile") {
        repeat(200) {
          serializer.serializeCache(file, storage)
        }
      }
        .warmupIterations(0)
        .attempts(1).startAsSubtest()

      Benchmark.newBenchmark("${testInfo.displayName} - DeserializationFromFile") {
        repeat(200) {
          serializer.deserializeCache(file).getOrThrow()
        }
      }
        .warmupIterations(0)
        .attempts(1).startAsSubtest()
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
      storageBuilder.addEntity(ContentRootEntity(manager.getOrCreateFromUrl(VfsUtilCore.pathToUrl("$newFolder/url${it}")), emptyList(), MySource) {
        this.module = module
      })
    }

    val replaceStorage = MutableEntityStorage.create()
    val replaceModule = ModuleEntity("data", emptyList(), MySource)
    replaceStorage.addEntity(replaceModule)
    repeat(1_000) {
      replaceStorage.addEntity(ContentRootEntity(manager.getOrCreateFromUrl(VfsUtilCore.pathToUrl("$newFolder/url${it}")), emptyList(), MySource) {
        this.module = replaceModule
      })
    }

    Benchmark.newBenchmark(testInfo.displayName) {
      storageBuilder.replaceBySource({ true }, replaceStorage)
    }
      .warmupIterations(0)
      .attempts(1).start()
  }

  @Test
  fun `project model updates`(testInfo: TestInfo) {
    Benchmark.newBenchmark(testInfo.displayName) {
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
      .attempts(1).start()
  }

  @Test
  fun `10_000 orphan content roots to modules`(testInfo: TestInfo) {
    val manager = WorkspaceModel.getInstance(projectModel.project).getVirtualFileUrlManager()
    val newFolder = tempFolder.newRandomDirectory()

    Benchmark.newBenchmark(testInfo.displayName) {
      runWriteActionAndWait {
        measureTimeMillis {
          projectModel.project.service<EntitiesOrphanage>().update {
            repeat(10_000) { counter ->
              it addEntity ModuleEntity("Module$counter", emptyList(), OrphanageWorkerEntitySource) {
                contentRoots = listOf(ContentRootEntity(manager.getOrCreateFromUrl(VfsUtilCore.pathToUrl("$newFolder/data$counter")), emptyList(), MySource))
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
      .attempts(1).start()

    ApplicationManager.getApplication().invokeAndWait {
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }

    projectModel.project.workspaceModel.currentSnapshot.entities(ModuleEntity::class.java).forEach {
      Assertions.assertTrue(it.contentRoots.isNotEmpty())
    }
  }

  @Test
  fun `10_000 orphan source roots to modules`(testInfo: TestInfo) {
    val newFolder = VfsUtilCore.pathToUrl(tempFolder.newRandomDirectory().toString())
    val manager = WorkspaceModel.getInstance(projectModel.project).getVirtualFileUrlManager()

    Benchmark.newBenchmark(testInfo.displayName) {
      runWriteActionAndWait {
        measureTimeMillis {
          projectModel.project.service<EntitiesOrphanage>().update {
            repeat(10_000) { counter ->
              it addEntity ModuleEntity("Module$counter", emptyList(), OrphanageWorkerEntitySource) {
                contentRoots = listOf(
                  ContentRootEntity(manager.getOrCreateFromUrl("$newFolder/data$counter"), emptyList(), OrphanageWorkerEntitySource) {
                    this.sourceRoots = listOf(
                      SourceRootEntity(manager.getOrCreateFromUrl("$newFolder/one$counter"), DEFAULT_SOURCE_ROOT_TYPE_ID, MySource),
                      SourceRootEntity(manager.getOrCreateFromUrl("$newFolder/two$counter"), DEFAULT_SOURCE_ROOT_TYPE_ID, MySource),
                      SourceRootEntity(manager.getOrCreateFromUrl("$newFolder/three$counter"), DEFAULT_SOURCE_ROOT_TYPE_ID, MySource),
                      SourceRootEntity(manager.getOrCreateFromUrl("$newFolder/four$counter"), DEFAULT_SOURCE_ROOT_TYPE_ID, MySource),
                      SourceRootEntity(manager.getOrCreateFromUrl("$newFolder/five$counter"), DEFAULT_SOURCE_ROOT_TYPE_ID, MySource),
                    )
                  })
              }
            }
          }

          WorkspaceModel.getInstance(projectModel.project).updateProjectModel {
            repeat(10_000) { counter ->
              it addEntity ModuleEntity("Module$counter", emptyList(), MySource) {
                contentRoots = listOf(ContentRootEntity(manager.getOrCreateFromUrl("$newFolder/data$counter"), emptyList(), MySource))
              }
            }
          }
        }
      }
    }
      .warmupIterations(0)
      .attempts(1).start()

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
    val manager = WorkspaceModel.getInstance(projectModel.project).getVirtualFileUrlManager()

    Benchmark.newBenchmark(testInfo.displayName) {
      runWriteActionAndWait {
        measureTimeMillis {
          projectModel.project.service<EntitiesOrphanage>().update {
            repeat(10_000) { counter ->
              it addEntity ModuleEntity("Module$counter", emptyList(), OrphanageWorkerEntitySource) {
                contentRoots = List(10) { contentCounter ->
                  ContentRootEntity(manager.getOrCreateFromUrl(VfsUtilCore.pathToUrl("$newFolder/data$contentCounter$counter")), emptyList(), OrphanageWorkerEntitySource) {
                    sourceRoots = List(10) { sourceCounter ->
                      SourceRootEntity(manager.getOrCreateFromUrl(VfsUtilCore.pathToUrl("$newFolder/one$sourceCounter$contentCounter$counter")), DEFAULT_SOURCE_ROOT_TYPE_ID, MySource)
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
                  ContentRootEntity(manager.getOrCreateFromUrl(VfsUtilCore.pathToUrl("$newFolder/data$contentCounter$counter")), emptyList(), OrphanageWorkerEntitySource)
                }
              }
            }
          }
        }
      }
    }
      .warmupIterations(0)
      .attempts(1).start()

    ApplicationManager.getApplication().invokeAndWait {
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }

    projectModel.project.workspaceModel.currentSnapshot.entities(ModuleEntity::class.java).forEach {
      Assertions.assertTrue(it.contentRoots.isNotEmpty() && it.contentRoots.all { it.sourceRoots.size == 10 })
    }
  }

  @Test
  fun `update storage via replaceProjectModel`(testInfo: TestInfo) {
    Benchmark.newBenchmark(testInfo.displayName) {
      runWriteActionAndWait {
        repeat(1000) {
          val builderSnapshot = (WorkspaceModel.getInstance(projectModel.project) as WorkspaceModelInternal).getBuilderSnapshot()
          builderSnapshot.builder addEntity ModuleEntity("Module$it", emptyList(), MySource)
          (WorkspaceModel.getInstance(projectModel.project) as WorkspaceModelInternal).replaceWorkspaceModel("update storage via replaceProjectModel", builderSnapshot.getStorageReplacement())
        }
      }
    }
      .warmupIterations(0)
      .attempts(1).start()

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

  @OptIn(EntityStorageInstrumentationApi::class)
  @Test
  fun `collect changes`(testInfo: TestInfo) {
    val builders = List(1000) {
      val builder = MutableEntityStorage.create() as MutableEntityStorageInstrumentation

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
          mutable.modifyNamedEntity(namedEntity) {
            this.myName = "newName$it"
          }
        }
        repeat(1000) {
          mutable addEntity NamedEntity("Hey$it", MySource)
        }
        mutable.entities(ChildEntity::class.java).forEach { mutable.removeEntity(it) }
        mutable.entities(OoChildWithNullableParentEntity::class.java).forEach {
          mutable.modifyOoChildWithNullableParentEntity(it) {
            this.parentEntity = null
          }
        }
      } as MutableEntityStorageInstrumentation
    }

    Benchmark.newBenchmark(testInfo.displayName) {
      builders.forEach { it.collectChanges() }
    }
      .warmupIterations(0)
      .attempts(1).start()
  }

  @Test
  fun `applyChangesFrom operation`(testInfo: TestInfo) {
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
          mutable.modifyNamedEntity(namedEntity) {
            this.myName = "newName$it"
          }
        }
        repeat(1000) {
          mutable addEntity NamedEntity("Hey$it", MySource)
        }
        mutable.entities(ChildEntity::class.java).forEach { mutable.removeEntity(it) }
        mutable.entities(OoChildWithNullableParentEntity::class.java).forEach {
          mutable.modifyOoChildWithNullableParentEntity(it) {
            this.parentEntity = null
          }
        }
      }
    }

    Benchmark.newBenchmark(testInfo.displayName) {
      builders.zip(newBuilders).forEach { (initial, update) ->
        initial.applyChangesFrom(update)
      }
    }
      .warmupIterations(0)
      .attempts(1).start()
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

    Benchmark.newBenchmark(testInfo.displayName) {
      builders.forEach { builder ->
        // Populate builder with changes
        repeat(1000) {
          val namedEntity = builder.resolve(NameId("MyName$it"))!!
          builder.modifyNamedEntity(namedEntity) {
            this.children = listOf(NamedChildEntity("prop", MySource))
          }
        }
        builder.entities(ChildEntity::class.java).forEach { builder.removeEntity(it) }
        builder.entities(OoChildWithNullableParentEntity::class.java).forEach {
          builder.modifyOoChildWithNullableParentEntity(it) {
            this.parentEntity = null
          }
        }
      }
    }
      .warmupIterations(0)
      .attempts(1).start()
  }

  @Test
  fun `request of cache`(testInfo: TestInfo) {
    CacheResetTracker.enable()
    println("Create snapshot")
    val baseSize = 500
    val smallBaseSize = 10
    TracedSnapshotCache.LOG_QUEUE_MAX_SIZE = baseSize * smallBaseSize
    val snapshots = List(baseSize / 2) {
      val builder = MutableEntityStorage.create()

      // Set initial state
      repeat(baseSize) {
        builder addEntity NamedEntity("MyName$it", MySource)
      }
      repeat(baseSize) {
        val parent = builder addEntity ParentEntity("data$it", MySource)

        val data = if (it % 2 == 0) "ExternalInfo" else "InternalInfo"
        builder.getMutableExternalMapping(externalMappingKey).addMapping(parent, data)
      }
      repeat(baseSize) {
        builder addEntity ParentMultipleEntity("data$it", MySource) {
          this.children = List(smallBaseSize) {
            ChildMultipleEntity("data$it", MySource)
          }
        }
      }
      builder.toSnapshot()
    }

    val namesOfNamedEntities = entities<NamedEntity>().map { it.myName }
    val sourcesByName = entities<NamedEntity>().groupBy({ it.myName }, { it.entitySource })
    val childData = entities<ParentMultipleEntity>().flatMap { parentEntity, _ -> parentEntity.children }.map { it.childData }

    // Do first request
    Benchmark.newBenchmark("${testInfo.displayName} - First Access") {
      snapshots.forEach { snapshot ->
        snapshot.cached(namesOfNamedEntities)
        snapshot.cached(sourcesByName)
        snapshot.cached(childData)
      }
    }
      .warmupIterations(0)
      .attempts(1).startAsSubtest()

    // Do second request without any modifications
    Benchmark.newBenchmark("${testInfo.displayName} - Second Access - No Changes") {
      snapshots.forEach { snapshot ->
        snapshot.cached(namesOfNamedEntities)
        snapshot.cached(sourcesByName)
        snapshot.cached(childData)
      }
    }
      .warmupIterations(0)
      .attempts(1).startAsSubtest()

    println("Modify after second read")
    // Modify snapshots
    val newSnapshots = snapshots.map { snapshot ->
      val builder = snapshot.toBuilder()
      repeat(baseSize / 10) {
        builder addEntity NamedEntity("MyNameXYZ$it", MySource)
      }
      repeat(baseSize / 2) { // Half of all entities
        val namedEntity = builder.resolve(NameId("MyName$it"))!!
        builder.modifyNamedEntity(namedEntity) {
          this.myName = "newName$it"
        }
      }
      val mutableMapping = builder.getMutableExternalMapping(externalMappingKey)
      mutableMapping.getEntities("ExternalInfo").take(baseSize / 4).forEach {
        mutableMapping.addMapping(it, "AnotherMapping")
      }

      builder.entities(ChildMultipleEntity::class.java).filter { it.childData.removePrefix("data").toInt() % 2 == 0 }.forEach {
        builder.removeEntity(it)
      }
      builder.toSnapshot()
    }

    println("Finish second modification")
    Assertions.assertFalse(CacheResetTracker.cacheReset)

    println("Start third read...")
    // Do request after modifications
    Benchmark.newBenchmark("${testInfo.displayName} - Third Access - After Modification") {
      newSnapshots.forEach { snapshot ->
        snapshot.cached(namesOfNamedEntities)
        snapshot.cached(sourcesByName)
        snapshot.cached(childData)
      }
    }
      .warmupIterations(0)
      .attempts(1).startAsSubtest()

    println("Modify snapshots second time")
    val snapshotsWithLotOfUpdates = newSnapshots.map { snapshot ->
      var currentSnapshot = snapshot
      repeat(smallBaseSize) { outerLoop ->
        val builder = currentSnapshot.toBuilder()

        repeat(smallBaseSize) {
          builder addEntity NamedEntity("MyName--$outerLoop-$it", MySource)
        }
        // Remove some random entities
        builder.entities(NamedEntity::class.java).withIndex().filter { it.index % (outerLoop + 1) == 0 }.forEach { (_, value) ->
          builder.removeEntity(value)
        }

        val mutableMapping = builder.getMutableExternalMapping(externalMappingKey)
        mutableMapping.getEntities("ExternalInfo").take(outerLoop).forEach {
          mutableMapping.addMapping(it, "AnotherMapping")
        }

        builder.entities(ChildMultipleEntity::class.java).filter { it.childData.removePrefix("data").toInt() % (outerLoop + 1) == 0 }.forEach {
          builder.removeEntity(it)
        }

        currentSnapshot = builder.toSnapshot()
      }
      currentSnapshot
    }

    Assertions.assertFalse(CacheResetTracker.cacheReset)

    Benchmark.newBenchmark("${testInfo.displayName} - Fourth Access - After Second Modification") {
      snapshotsWithLotOfUpdates.forEach { snapshot ->
        snapshot.cached(namesOfNamedEntities)
        snapshot.cached(sourcesByName)
        snapshot.cached(childData)
      }
    }
      .warmupIterations(0)
      .attempts(1).startAsSubtest()

    println("Modify snapshots third time")
    val snapshotsWithTonsOfUpdates = snapshotsWithLotOfUpdates.map { snapshot ->
      var currentSnapshot = snapshot
      repeat(smallBaseSize + 1) { outerLoop ->
        val builder = currentSnapshot.toBuilder()

        repeat(baseSize) {
          builder addEntity NamedEntity("MyName-X$outerLoop-$it", MySource)
        }

        currentSnapshot = builder.toSnapshot()
      }
      currentSnapshot
    }

    Assertions.assertTrue(CacheResetTracker.cacheReset)

    println("Read fourth time")
    Benchmark.newBenchmark("${testInfo.displayName} - Fifth Access - After a Lot of Modifications") {
      snapshotsWithTonsOfUpdates.forEach { snapshot ->
        snapshot.cached(namesOfNamedEntities)
        snapshot.cached(sourcesByName)
        snapshot.cached(childData)
      }
    }
      .warmupIterations(0)
      .attempts(1).startAsSubtest()
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
      builder.getMutableExternalMapping(externalMappingKey).addMapping(entity, "data$index")
    }

    measureOperation("getEntity", singleBuilderEntities, buildersToEntity) { index, (builder, _) ->
      builder.getExternalMapping(externalMappingKey).getEntities("data$index")
    }

    measureOperation("getData", singleBuilderEntities, buildersToEntity) { _, (builder, entity) ->
      builder.getExternalMapping(externalMappingKey).getDataByEntity(entity)
    }

    // Measure removal
    measureOperation("removeMapping", singleBuilderEntities, buildersToEntity) { _, (builder, entity) ->
      builder.getMutableExternalMapping(externalMappingKey).removeMapping(entity)
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

    Benchmark.newBenchmark(testInfo.displayName) {
      repeat(size) {
        val myBuilder = mySnapshot.toBuilder()
        val entity = myBuilder.resolve(NameId("Name$it"))!!
        myBuilder.getMutableExternalMapping(externalMappingKey).addMapping(entity, "data$it")
        mySnapshot = myBuilder.toSnapshot()
      }
    }
      .warmupIterations(0)
      .attempts(1).start()
  }

  @ParameterizedTest
  @ValueSource(ints = [10, 100, 1_000, 10_000, 100_000, 1_000_000])
  fun `recalculate versus cache`(size: Int, testInfo: TestInfo) {
    TracedSnapshotCache.LOG_QUEUE_MAX_SIZE = size * 10
    val q = entities<NamedEntity>().map { it.myName }

    val builder = MutableEntityStorage.create()
    repeat(size) {
      builder addEntity NamedEntity("Data$it", MySource)
    }
    var snapshot = builder.toSnapshot()

    println("Test one --- Raw recalculate")
    Benchmark.newBenchmark(testInfo.displayName + " raw calculate - size: $size") {
      val time = measureTime {
        snapshot.entities<NamedEntity>().map { it.myName }.toList()
      }
      println("Raw recalculate. size: $size, time: $time.")
    }
      .warmupIterations(0)
      .attempts(1).start()

    println()
    println("Test two --- First calculate")
    Benchmark.newBenchmark(testInfo.displayName + "- first calculate - size: $size") {
      val time2 = measureTime {
        snapshot.cached(q)
      }
      println("First recalculate. size: $size, time: $time2.")
    }
      .warmupIterations(0)
      .attempts(1).start()

    println()
    println("Test three --- Unmodified second access")
    Benchmark.newBenchmark(testInfo.displayName + "- second access - size: $size") {
      val time3 = measureTime {
        snapshot.cached(q)
      }
      println("Unmodified second access. size: $size, time: $time3.")
    }
      .warmupIterations(0)
      .attempts(1).start()

    snapshot = snapshot.toBuilder().also { it.addEntity(NamedEntity("XX", MySource)) }.toSnapshot()

    println()
    println("Test four --- Add one entity")
    Benchmark.newBenchmark(testInfo.displayName + "- Add one entity - size: $size") {
      val time4 = measureTime {
        snapshot.cached(q)
      }
      println("Add one entity. size: $size, time: $time4.")
    }
      .warmupIterations(0)
      .attempts(1).start()

    snapshot = snapshot.toBuilder().also { builder1 ->
      // Remove 10% of entities
      builder1.entities<NamedEntity>().take(size / 10).forEach { builder1.removeEntity(it) }

      // Modify 10% of entities
      builder1.entities<NamedEntity>().take(size / 10).forEach {
        builder1.modifyNamedEntity(it) {
          this.myName += "MyName"
        }
      }

      // Add 10% of entities
      repeat(size / 10) {
        builder1.addEntity(NamedEntity("XXY$it", MySource))
      }
    }.toSnapshot()

    println()
    println("Test five --- Update 10% of entities")
    Benchmark.newBenchmark(testInfo.displayName + "- Affect 10% of entities - size: $size") {
      val time5 = measureTime {
        snapshot.cached(q)
      }
      println("Update 10% of entities. size: $size, time: $time5.")
    }
      .warmupIterations(0)
      .attempts(1).start()
  }

  @ParameterizedTest
  @ValueSource(ints = [10, 100, 1_000, 10_000, 100_000])
  fun `recalculate versus cache on tricky case`(size: Int, testInfo: TestInfo) {
    TracedSnapshotCache.LOG_QUEUE_MAX_SIZE = size * 10
    val q = entities<NamedEntity>()
      .flatMap { namedEntity, _ -> namedEntity.children }
      .map { it.childProperty }
      .map { it + "XXXX" }

    val builder = MutableEntityStorage.create()
    repeat(size) {
      builder addEntity NamedEntity("Data$it", MySource) {
        this.children = List(10) {
          NamedChildEntity("Child$it", MySource)
        }
      }
    }
    var snapshot = builder.toSnapshot()

    println("Test one --- Raw recalculate")
    Benchmark.newBenchmark(testInfo.displayName + " raw calculate - size: $size") {
      val time = measureTime {
        snapshot.entities<NamedEntity>()
          .flatMap { it.children }
          .map { it.childProperty }
          .map { it + "XXXX" }
          .toList()
      }
      println("Raw recalculate. size: $size, time: $time.")
    }
      .warmupIterations(0)
      .attempts(1).start()

    println()
    println("Test two --- First calculate")
    Benchmark.newBenchmark(testInfo.displayName + "- first calculate - size: $size") {
      val time2 = measureTime {
        snapshot.cached(q)
      }
      println("First recalculate. size: $size, time: $time2.")
    }
      .warmupIterations(0)
      .attempts(1).start()

    println()
    println("Test three --- Unmodified second access")
    Benchmark.newBenchmark(testInfo.displayName + "- second access - size: $size") {
      val time3 = measureTime {
        snapshot.cached(q)
      }
      println("Unmodified second access. size: $size, time: $time3.")
    }
      .warmupIterations(0)
      .attempts(1).start()

    snapshot = snapshot.toBuilder().also {
      it addEntity NamedEntity("XX", MySource) {
        this.children = listOf(NamedChildEntity("Hey", MySource))
      }
    }.toSnapshot()

    println()
    println("Test four --- Add one entity")
    Benchmark.newBenchmark(testInfo.displayName + "- Add one entity - size: $size") {
      val time4 = measureTime {
        snapshot.cached(q)
      }
      println("Add one entity. size: $size, time: $time4.")
    }
      .warmupIterations(0)
      .attempts(1).start()

    snapshot = snapshot.toBuilder().also { builder1 ->
      // Remove 10% of entities
      builder1.entities<NamedEntity>().take(size / 10).forEach { builder1.removeEntity(it) }

      // Modify 10% of entities
      builder1.entities<NamedEntity>().take(size / 10).forEach {
        builder1.modifyNamedEntity(it) {
          this.myName += "MyName"
        }
      }
      builder1.entities<NamedChildEntity>().toList().takeLast(size / 10).forEach {
        builder1.modifyNamedChildEntity(it) {
          this.childProperty = "Prop"
        }
      }

      // Add 10% of entities
      repeat(size / 10) {
        builder1 addEntity NamedEntity("XXY$it", MySource) {
          this.children = List(10) { NamedChildEntity("Prop", MySource) }
        }
      }
    }.toSnapshot()

    println()
    println("Test five --- Update 10% of entities")
    Benchmark.newBenchmark(testInfo.displayName + "- Affect 10% of entities - size: $size") {
      val time51 = measureTime {
        snapshot.cached(q)
      }
      val time5 = time51
      println("Update 10% of entities. size: $size, time: $time5.")
    }
      .warmupIterations(0)
      .attempts(1).start()
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `find rate when cache is better on adding entities`(preInitializeEntities: Boolean) {
    // This test finds a percentage of changes when the cache calculation remains faster than full recalculation of data
    TracedSnapshotCache.LOG_QUEUE_MAX_SIZE = 2_000_000
    listOf(1000, 10_000, 100_000, 500_000).forEach { size ->
      val builder = MutableEntityStorage.create()
      repeat(size) {
        builder addEntity NamedEntity("Name$it", MySource)
      }
      val baseSnapshot = builder.toSnapshot()

      val q = entities<NamedEntity>().map { it.myName }

      baseSnapshot.cached(q)

      val perentages = ArrayList<Int>()
      val timesCalc = ArrayList<Duration>()
      val timesCache = ArrayList<Duration>()
      (1..100).forEach { percent ->
        val intBuilder = baseSnapshot.toBuilder()
        repeat((size / 100) * percent) { entitiesBatch ->
          intBuilder addEntity NamedEntity("MyEnt$entitiesBatch", MySource)
        }
        val newSnapshot = intBuilder.toSnapshot()

        if (preInitializeEntities) {
          newSnapshot.entities<NamedEntity>().map { it.myName }.toList()
        }
        val timeCalc = measureTime { newSnapshot.entities<NamedEntity>().map { it.myName }.toList() }
        val timeCached = measureTime {
          newSnapshot.cached(q)
        }
        timesCalc += timeCalc
        timesCache += timeCached
        if (timeCached < timeCalc) {
          perentages.add(percent)
        }
      }
      val averageCache = timesCache.map { it.inWholeMilliseconds }.average()
      val averageCalc = timesCalc.map { it.inWholeMilliseconds }.average()
      val maxPerc = perentages.sortedDescending().take(5)
      if (preInitializeEntities) {
        println("Pre initialize entities. Size: $size, average cache: $averageCache ms, average calc: $averageCalc ms, maxPerc: $maxPerc")
      }
      else {
        println("Do not initialize entities. Size: $size, average cache: $averageCache ms, average calc: $averageCalc ms, maxPerc: $maxPerc")
      }
    }
  }

  @Test
  fun `find rate when cache is better on modifying entities`() {
    // This test finds a percentage of changes when the cache calculation remains faster than full recalculation of data
    TracedSnapshotCache.LOG_QUEUE_MAX_SIZE = 2_000_000
    listOf(1000, 10_000, 100_000, 500_000).forEach { size ->
      val builder = MutableEntityStorage.create()
      repeat(size) {
        builder addEntity NamedEntity("Name$it", MySource)
      }
      val baseSnapshot = builder.toSnapshot()

      val q = entities<NamedEntity>().map { it.myName }

      baseSnapshot.cached(q)

      val perentages = ArrayList<Int>()
      val timesCalc = ArrayList<Duration>()
      val timesCache = ArrayList<Duration>()
      (1..100).forEach { percent ->
        val intBuilder = baseSnapshot.toBuilder()
        repeat((size / 100) * percent) { entitiesBatch ->
          val entity = intBuilder.resolve(NameId("Name$entitiesBatch"))!!
          intBuilder.modifyNamedEntity(entity) {
            this.myName = "Another$entitiesBatch"
          }
        }
        val newSnapshot = intBuilder.toSnapshot()

        val timeCalc = measureTime { newSnapshot.entities<NamedEntity>().map { it.myName }.toList() }
        val timeCached = measureTime {
          newSnapshot.cached(q)
        }
        timesCalc += timeCalc
        timesCache += timeCached
        if (timeCached < timeCalc) {
          perentages.add(percent)
        }
      }
      val averageCache = timesCache.map { it.inWholeMilliseconds }.average()
      val averageCalc = timesCalc.map { it.inWholeMilliseconds }.average()
      val maxPerc = perentages.sortedDescending().take(5)
      println("Size: $size, average cache: $averageCache, average calc: $averageCalc, maxPerc: $maxPerc")
    }
  }

  @ParameterizedTest
  @ValueSource(ints = [10, 1_000, 100_000, 10_000_000])
  fun `get for kotlin persistent map`(size: Int) {
    val requestSize = 10_000_000

    Benchmark.newBenchmark(size.toString()) {
      testPersistentMap(size, requestSize)
    }
      .warmupIterations(0)
      .attempts(1).startAsSubtest()
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

  @OptIn(EntityStorageInstrumentationApi::class)
  @Test
  fun `cache with adding modules`() {
    val q = entities<NamedEntity>().map { it.myName }

    val builder = MutableEntityStorage.create()
    repeat(1000) {
      builder addEntity NamedEntity("Data$it", MySource)
    }

    var snapshot = builder.toSnapshot() as ImmutableEntityStorageInstrumentation

    var cache = cache()
    cache.cached(q, snapshot, null)

    Benchmark.newBenchmark("Cache - adding 1000 modules") {
      repeat(1000) { count ->
        val newBuilder = snapshot.toBuilder().also { it addEntity NamedEntity("AnotherData$count", MySource) }
        val newCache = cache()
        val newSnapshot = newBuilder.toSnapshot() as ImmutableEntityStorageInstrumentation
        val changes = ChangeOnVersionedChange((newBuilder as MutableEntityStorageInstrumentation).collectChanges().values.asSequence().flatten())
        newCache.pullCache(newSnapshot, cache, changes)
        newCache.cached(q, newSnapshot, snapshot)
        snapshot = newSnapshot
        cache = newCache
      }
    }
      .warmupIterations(0)
      .attempts(1).startAsSubtest()
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  @Test
  fun `cache with removing modules`() {
    val q = entities<NamedEntity>().map { it.myName }

    val builder = MutableEntityStorage.create()
    repeat(1000) {
      builder addEntity NamedEntity("Data$it", MySource)
      builder addEntity NamedEntity("Another$it", MySource)
    }

    var snapshot = builder.toSnapshot() as ImmutableEntityStorageInstrumentation

    var cache = cache()
    cache.cached(q, snapshot, null)

    Benchmark.newBenchmark("Cache - removing 1000 modules") {
      repeat(1000) { count ->
        val newBuilder = snapshot.toBuilder().also {
          val id = it.resolve(NameId("Another$count"))!!
          it.removeEntity(id)
        }
        val newCache = cache()
        val newSnapshot = newBuilder.toSnapshot() as ImmutableEntityStorageInstrumentation
        val changes = ChangeOnVersionedChange((newBuilder as MutableEntityStorageInstrumentation).collectChanges().values.asSequence().flatten())
        newCache.pullCache(newSnapshot, cache, changes)
        newCache.cached(q, newSnapshot, snapshot)
        snapshot = newSnapshot
        cache = newCache
      }
    }
      .warmupIterations(0)
      .attempts(1).startAsSubtest()
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  @Test
  fun `cache with modification modules`() {
    val q = entities<NamedEntity>().map { it.myName }

    val builder = MutableEntityStorage.create()
    repeat(1000) {
      builder addEntity NamedEntity("Data$it", MySource)
      builder addEntity NamedEntity("Another$it", MySource)
    }

    var snapshot = builder.toSnapshot() as ImmutableEntityStorageInstrumentation

    var cache = cache()
    cache.cached(q, snapshot, null)

    Benchmark.newBenchmark("Cache - modifying 1000 modules") {
      repeat(1000) { count ->
        val newBuilder = snapshot.toBuilder().also {
          val id = it.resolve(NameId("Another$count"))!!
          it.modifyNamedEntity(id) {
            this.myName = "Third$count"
          }
        }
        val newCache = cache()
        val newSnapshot = newBuilder.toSnapshot() as ImmutableEntityStorageInstrumentation
        val changes = ChangeOnVersionedChange((newBuilder as MutableEntityStorageInstrumentation).collectChanges().values.asSequence().flatten())
        newCache.pullCache(newSnapshot, cache, changes)
        newCache.cached(q, newSnapshot, snapshot)
        snapshot = newSnapshot
        cache = newCache
      }
    }
      .warmupIterations(0)
      .attempts(1).startAsSubtest()
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  @Test
  fun `cache with bunches of unrelated changes`() {
    val q = entities<NamedEntity>().map { it.myName }

    val builder = MutableEntityStorage.create()
    repeat(1000) {
      builder addEntity NamedEntity("Data$it", MySource)
      builder addEntity NamedEntity("Another$it", MySource)
    }

    var snapshot = builder.toSnapshot() as ImmutableEntityStorageInstrumentation

    var cache = cache()
    cache.cached(q, snapshot, null)

    Benchmark.newBenchmark("Cache - unrelated modifications 1000 times") {
      repeat(1000) { count ->
        val newBuilder = snapshot.toBuilder().also { builder ->
          if (count % 2 == 0) {
            repeat(1000) { builder addEntity ParentEntity("", MySource) }
          }
          else {
            builder.entities<ParentEntity>().toList().forEach { builder.removeEntity(it) }
          }
        }
        val newCache = cache()
        val newSnapshot = newBuilder.toSnapshot() as ImmutableEntityStorageInstrumentation
        val changes = ChangeOnVersionedChange((newBuilder as MutableEntityStorageInstrumentation).collectChanges().values.asSequence().flatten())
        newCache.pullCache(newSnapshot, cache, changes)
        newCache.cached(q, newSnapshot, snapshot)
        snapshot = newSnapshot
        cache = newCache
      }
    }
      .warmupIterations(0)
      .attempts(1).startAsSubtest()
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  @Test
  fun `cache with bunches of related changes`() {
    val q = entities<NamedEntity>().map { it.myName }

    val builder = MutableEntityStorage.create()

    var snapshot = builder.toSnapshot() as ImmutableEntityStorageInstrumentation

    var cache = cache()
    cache.cached(q, snapshot, null)

    Benchmark.newBenchmark("Cache - related modifications 1000 times") {
      repeat(1000) { count ->
        val newBuilder = snapshot.toBuilder().also { builder ->
          if (count % 2 == 0) {
            repeat(1000) { builder addEntity NamedEntity("XXX$it", MySource) }
          }
          else {
            builder.entities<NamedEntity>().toList().forEach { builder.removeEntity(it) }
          }
        }
        val newCache = cache()
        val newSnapshot = newBuilder.toSnapshot() as ImmutableEntityStorageInstrumentation
        val changes = ChangeOnVersionedChange((newBuilder as MutableEntityStorageInstrumentation).collectChanges().values.asSequence().flatten())
        newCache.pullCache(newSnapshot, cache, changes)
        newCache.cached(q, newSnapshot, snapshot)
        snapshot = newSnapshot
        cache = newCache
      }
    }
      .warmupIterations(0)
      .attempts(1).startAsSubtest()
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  @Test
  fun `cache with bunches of related changes with many queries`() {
    val qs = List(100) { entities<NamedEntity>().map { it.myName } }

    val builder = MutableEntityStorage.create()

    var snapshot = builder.toSnapshot() as ImmutableEntityStorageInstrumentation

    var cache = cache()
    qs.forEach { q -> cache.cached(q, snapshot, null) }

    Benchmark.newBenchmark("Cache - many queries and related modifications 1000 times") {
      repeat(1000) { count ->
        val newBuilder = snapshot.toBuilder().also { builder ->
          if (count % 2 == 0) {
            repeat(1000) { builder addEntity NamedEntity("XXX$it", MySource) }
          }
          else {
            builder.entities<NamedEntity>().toList().forEach { builder.removeEntity(it) }
          }
        }
        val newCache = cache()
        val newSnapshot = newBuilder.toSnapshot() as ImmutableEntityStorageInstrumentation
        val changes = ChangeOnVersionedChange((newBuilder as MutableEntityStorageInstrumentation).collectChanges().values.asSequence().flatten())
        newCache.pullCache(newSnapshot, cache, changes)
        qs.forEach { q -> newCache.cached(q, newSnapshot, snapshot) }
        snapshot = newSnapshot
        cache = newCache
      }
    }
      .warmupIterations(0)
      .attempts(1).startAsSubtest()
  }

  @Test
  fun `changelog performance - one to many children adding`(testInfo: TestInfo) {
    val times = 20_000
    val initialSnapshot = MutableEntityStorage.create().also { builder ->
      repeat(times) {
        builder addEntity ParentMultipleEntity("Parent_$it", MySource) {
          this.children = List(5) { (ChildMultipleEntity("Child_$it", MySource)) }
        }
      }
    }.toSnapshot()
    val builder = initialSnapshot.toBuilder()

    // Fill builder with some changes. In this way we'll get more internal work for "merging" with the existing change.
    builder.entities<ParentMultipleEntity>().forEach { parent ->
      builder.modifyParentMultipleEntity(parent) {
        this.children = emptyList()
      }
    }
    Benchmark.newBenchmark(testInfo.displayName) {
      builder.entities<ParentMultipleEntity>().forEach { parent ->
        builder.modifyParentMultipleEntity(parent) {
          this.children += List(10) { ChildMultipleEntity("Child_2_$it", MySource) }
        }
      }
    }
      .warmupIterations(0)
      .attempts(1).start()

    Benchmark.newBenchmark(testInfo.displayName + " - applyChangesFrom") {
      initialSnapshot.toBuilder().applyChangesFrom(builder)
    }
      .warmupIterations(0)
      .attempts(1).start()
  }

  private fun measureOperation(launchName: String, singleBuilderEntities: List<Pair<MutableEntityStorage, NamedEntity>>,
                               perBuilderEntities: List<Pair<MutableEntityStorage, NamedEntity>>,
                               operation: (Int, Pair<MutableEntityStorage, NamedEntity>) -> Unit): Unit {
    Benchmark.newBenchmark("$launchName-singleBuilderEntities") {
      singleBuilderEntities.forEachIndexed(operation)
    }
      .warmupIterations(0)
      .attempts(1).startAsSubtest()

    Benchmark.newBenchmark("$launchName-perBuilderEntities") {
      perBuilderEntities.forEachIndexed(operation)
    }
      .warmupIterations(0)
      .attempts(1).startAsSubtest()
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
      return JpsProjectConfigLocation.FileBased(iprFile, iprFile.parent!!)
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
