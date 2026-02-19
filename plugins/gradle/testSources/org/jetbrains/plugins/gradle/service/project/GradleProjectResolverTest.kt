// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import org.junit.jupiter.api.Test
import java.nio.file.Path

class GradleProjectResolverTest : GradleProjectResolverTestCase() {

  @Test
  fun `test content root merging`() {
    val projectPath = Path.of("path/to/project")
    val projectModel = createProjectModel(projectPath, ":")
    val externalProject = createExternalProject(projectPath)
    val resolverContext = createResolveContext(projectModel to externalProject)

    val expectedModuleNode = createModuleNode("project").apply {
      addChild(createSourceSetNode("main").apply {
        addChild(createContentRoot(projectPath.resolve("src/main")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE, projectPath.resolve("src/main/java").toString())
          data.storePath(ExternalSystemSourceType.RESOURCE, projectPath.resolve("src/main/resources").toString())
        })
      })
      addChild(createSourceSetNode("test").apply {
        addChild(createContentRoot(projectPath.resolve("src/test")).apply {
          data.storePath(ExternalSystemSourceType.TEST, projectPath.resolve("src/test/java").toString())
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, projectPath.resolve("src/test/resources").toString())
        })
      })
    }

    val actualModuleNode = createModuleNode("project").apply {
      addChild(createSourceSetNode("main").apply {
        addChild(createContentRoot(projectPath.resolve("src/main/java")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE, projectPath.resolve("src/main/java").toString())
        })
        addChild(createContentRoot(projectPath.resolve("src/main/resources")).apply {
          data.storePath(ExternalSystemSourceType.RESOURCE, projectPath.resolve("src/main/resources").toString())
        })
      })
      addChild(createSourceSetNode("test").apply {
        addChild(createContentRoot(projectPath.resolve("src/test/java")).apply {
          data.storePath(ExternalSystemSourceType.TEST, projectPath.resolve("src/test/java").toString())
        })
        addChild(createContentRoot(projectPath.resolve("src/test/resources")).apply {
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, projectPath.resolve("src/test/resources").toString())
        })
      })
    }

    GradleProjectResolver.mergeSourceSetContentRootsInModulePerSourceSetMode(resolverContext, mapOf(projectModel to actualModuleNode))

    assertModuleNodeEquals(expectedModuleNode, actualModuleNode)
  }

  @Test
  fun `test content root merging for custom sources`() {
    val projectPath = Path.of("path/to/project")
    val projectModel = createProjectModel(projectPath, ":")
    val externalProject = createExternalProject(projectPath)
    val resolverContext = createResolveContext(projectModel to externalProject)

    val expectedModuleNode = createModuleNode("project").apply {
      addChild(createSourceSetNode("main").apply {
        addChild(createContentRoot(projectPath.resolve("src")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE, projectPath.resolve("src/java").toString())
          data.storePath(ExternalSystemSourceType.RESOURCE, projectPath.resolve("src/resources").toString())
        })
      })
      addChild(createSourceSetNode("test").apply {
        addChild(createContentRoot(projectPath.resolve("testSrc")).apply {
          data.storePath(ExternalSystemSourceType.TEST, projectPath.resolve("testSrc/java").toString())
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, projectPath.resolve("testSrc/resources").toString())
        })
      })
    }

    val actualModuleNode = createModuleNode("project").apply {
      addChild(createSourceSetNode("main").apply {
        addChild(createContentRoot(projectPath.resolve("src/java")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE, projectPath.resolve("src/java").toString())
        })
        addChild(createContentRoot(projectPath.resolve("src/resources")).apply {
          data.storePath(ExternalSystemSourceType.RESOURCE, projectPath.resolve("src/resources").toString())
        })
      })
      addChild(createSourceSetNode("test").apply {
        addChild(createContentRoot(projectPath.resolve("testSrc/java")).apply {
          data.storePath(ExternalSystemSourceType.TEST, projectPath.resolve("testSrc/java").toString())
        })
        addChild(createContentRoot(projectPath.resolve("testSrc/resources")).apply {
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, projectPath.resolve("testSrc/resources").toString())
        })
      })
    }

    GradleProjectResolver.mergeSourceSetContentRootsInModulePerSourceSetMode(resolverContext, mapOf(projectModel to actualModuleNode))

    assertModuleNodeEquals(expectedModuleNode, actualModuleNode)
  }

  @Test
  fun `test content root merging for incomplete sources`() {
    val projectPath = Path.of("path/to/project")
    val projectModel = createProjectModel(projectPath, ":")
    val externalProject = createExternalProject(projectPath)
    val resolverContext = createResolveContext(projectModel to externalProject)

    run {
      val expectedModuleNode = createModuleNode("project").apply {
        addChild(createSourceSetNode("main").apply {
          addChild(createContentRoot(projectPath.resolve("src/main")).apply {
            data.storePath(ExternalSystemSourceType.SOURCE, projectPath.resolve("src/main/java").toString())
          })
        })
        addChild(createSourceSetNode("test").apply {
          addChild(createContentRoot(projectPath.resolve("src/test")).apply {
            data.storePath(ExternalSystemSourceType.TEST, projectPath.resolve("src/test/java").toString())
          })
        })
      }

      val actualModuleNode = createModuleNode("project").apply {
        addChild(createSourceSetNode("main").apply {
          addChild(createContentRoot(projectPath.resolve("src/main/java")).apply {
            data.storePath(ExternalSystemSourceType.SOURCE, projectPath.resolve("src/main/java").toString())
          })
        })
        addChild(createSourceSetNode("test").apply {
          addChild(createContentRoot(projectPath.resolve("src/test/java")).apply {
            data.storePath(ExternalSystemSourceType.TEST, projectPath.resolve("src/test/java").toString())
          })
        })
      }

      GradleProjectResolver.mergeSourceSetContentRootsInModulePerSourceSetMode(resolverContext, mapOf(projectModel to actualModuleNode))

      assertModuleNodeEquals(expectedModuleNode, actualModuleNode)
    }

    run {
      val expectedModuleNode = createModuleNode("project").apply {
        addChild(createSourceSetNode("main").apply {
          addChild(createContentRoot(projectPath.resolve("src/main")).apply {
            data.storePath(ExternalSystemSourceType.SOURCE, projectPath.resolve("src/main/java").toString())
          })
        })
      }

      val actualModuleNode = createModuleNode("project").apply {
        addChild(createSourceSetNode("main").apply {
          addChild(createContentRoot(projectPath.resolve("src/main/java")).apply {
            data.storePath(ExternalSystemSourceType.SOURCE, projectPath.resolve("src/main/java").toString())
          })
        })
      }

      GradleProjectResolver.mergeSourceSetContentRootsInModulePerSourceSetMode(resolverContext, mapOf(projectModel to actualModuleNode))

      assertModuleNodeEquals(expectedModuleNode, actualModuleNode)
    }
  }

  @Test
  fun `test content root merging for incomplete external sources`() {
    val rootPath = Path.of("path/to/root")
    val projectModel = createProjectModel(rootPath.resolve("projectRoot"), ":")
    val externalProject = createExternalProject(rootPath.resolve("projectRoot"))
    val resolverContext = createResolveContext(projectModel to externalProject)

    run {
      val expectedModuleNode = createModuleNode("project").apply {
        addChild(createSourceSetNode("main").apply {
          addChild(createContentRoot(rootPath.resolve("externalRoot/src/main")).apply {
            data.storePath(ExternalSystemSourceType.SOURCE, rootPath.resolve("externalRoot/src/main/java").toString())
          })
        })
        addChild(createSourceSetNode("test").apply {
          addChild(createContentRoot(rootPath.resolve("externalRoot/src/test")).apply {
            data.storePath(ExternalSystemSourceType.TEST, rootPath.resolve("externalRoot/src/test/java").toString())
          })
        })
      }

      val actualModuleNode = createModuleNode("project").apply {
        addChild(createSourceSetNode("main").apply {
          addChild(createContentRoot(rootPath.resolve("externalRoot/src/main/java")).apply {
            data.storePath(ExternalSystemSourceType.SOURCE, rootPath.resolve("externalRoot/src/main/java").toString())
          })
        })
        addChild(createSourceSetNode("test").apply {
          addChild(createContentRoot(rootPath.resolve("externalRoot/src/test/java")).apply {
            data.storePath(ExternalSystemSourceType.TEST, rootPath.resolve("externalRoot/src/test/java").toString())
          })
        })
      }

      GradleProjectResolver.mergeSourceSetContentRootsInModulePerSourceSetMode(resolverContext, mapOf(projectModel to actualModuleNode))

      assertModuleNodeEquals(expectedModuleNode, actualModuleNode)
    }

    run {
      val expectedModuleNode = createModuleNode("project").apply {
        addChild(createSourceSetNode("main").apply {
          addChild(createContentRoot(rootPath.resolve("externalRoot/src/main")).apply {
            data.storePath(ExternalSystemSourceType.SOURCE, rootPath.resolve("externalRoot/src/main/java").toString())
          })
        })
      }

      val actualModuleNode = createModuleNode("project").apply {
        addChild(createSourceSetNode("main").apply {
          addChild(createContentRoot(rootPath.resolve("externalRoot/src/main/java")).apply {
            data.storePath(ExternalSystemSourceType.SOURCE, rootPath.resolve("externalRoot/src/main/java").toString())
          })
        })
      }

      GradleProjectResolver.mergeSourceSetContentRootsInModulePerSourceSetMode(resolverContext, mapOf(projectModel to actualModuleNode))

      assertModuleNodeEquals(expectedModuleNode, actualModuleNode)
    }
  }

  @Test
  fun `test content root merging for flatten sources`() {
    val projectPath = Path.of("path/to/project")
    val projectModel = createProjectModel(projectPath, ":")
    val externalProject = createExternalProject(projectPath)
    val resolverContext = createResolveContext(projectModel to externalProject)

    val expectedModuleNode = createModuleNode("project").apply {
      addChild(createSourceSetNode("main").apply {
        addChild(createContentRoot(projectPath.resolve("src")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE, projectPath.resolve("src").toString())
        })
        addChild(createContentRoot(projectPath.resolve("resources")).apply {
          data.storePath(ExternalSystemSourceType.RESOURCE, projectPath.resolve("resources").toString())
        })
      })
      addChild(createSourceSetNode("test").apply {
        addChild(createContentRoot(projectPath.resolve("testSrc")).apply {
          data.storePath(ExternalSystemSourceType.TEST, projectPath.resolve("testSrc").toString())
        })
        addChild(createContentRoot(projectPath.resolve("testResources")).apply {
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, projectPath.resolve("testResources").toString())
        })
      })
    }

    val actualModuleNode = createModuleNode("project").apply {
      addChild(createSourceSetNode("main").apply {
        addChild(createContentRoot(projectPath.resolve("src")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE, projectPath.resolve("src").toString())
        })
        addChild(createContentRoot(projectPath.resolve("resources")).apply {
          data.storePath(ExternalSystemSourceType.RESOURCE, projectPath.resolve("resources").toString())
        })
      })
      addChild(createSourceSetNode("test").apply {
        addChild(createContentRoot(projectPath.resolve("testSrc")).apply {
          data.storePath(ExternalSystemSourceType.TEST, projectPath.resolve("testSrc").toString())
        })
        addChild(createContentRoot(projectPath.resolve("testResources")).apply {
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, projectPath.resolve("testResources").toString())
        })
      })
    }

    GradleProjectResolver.mergeSourceSetContentRootsInModulePerSourceSetMode(resolverContext, mapOf(projectModel to actualModuleNode))

    assertModuleNodeEquals(expectedModuleNode, actualModuleNode)
  }

  @Test
  fun `test content root merging for generated sources`() {
    val projectPath = Path.of("path/to/project")
    val projectBuildPath = projectPath.resolve("build")
    val projectModel = createProjectModel(projectPath, ":")
    val externalProject = createExternalProject(projectPath, projectBuildPath)
    val resolverContext = createResolveContext(projectModel to externalProject)

    val expectedModuleNode = createModuleNode("project").apply {
      addChild(createSourceSetNode("main").apply {
        addChild(createContentRoot(projectPath.resolve("src/main")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE, projectPath.resolve("src/main/java").toString())
          data.storePath(ExternalSystemSourceType.RESOURCE, projectPath.resolve("src/main/resources").toString())
        })
        addChild(createContentRoot(projectPath.resolve("build/generated/sources/annotationProcessor/java/main")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE_GENERATED, projectPath.resolve("build/generated/sources/annotationProcessor/java/main").toString())
        })
      })
      addChild(createSourceSetNode("test").apply {
        addChild(createContentRoot(projectPath.resolve("src/test")).apply {
          data.storePath(ExternalSystemSourceType.TEST, projectPath.resolve("src/test/java").toString())
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, projectPath.resolve("src/test/resources").toString())
        })
        addChild(createContentRoot(projectPath.resolve("build/generated/sources/annotationProcessor/java/test")).apply {
          data.storePath(ExternalSystemSourceType.TEST_GENERATED, projectPath.resolve("build/generated/sources/annotationProcessor/java/test").toString())
        })
      })
    }

    val actualModuleNode = createModuleNode("project").apply {
      addChild(createSourceSetNode("main").apply {
        addChild(createContentRoot(projectPath.resolve("src/main/java")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE, projectPath.resolve("src/main/java").toString())
        })
        addChild(createContentRoot(projectPath.resolve("src/main/resources")).apply {
          data.storePath(ExternalSystemSourceType.RESOURCE, projectPath.resolve("src/main/resources").toString())
        })
        addChild(createContentRoot(projectPath.resolve("build/generated/sources/annotationProcessor/java/main")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE_GENERATED, projectPath.resolve("build/generated/sources/annotationProcessor/java/main").toString())
        })
      })
      addChild(createSourceSetNode("test").apply {
        addChild(createContentRoot(projectPath.resolve("src/test/java")).apply {
          data.storePath(ExternalSystemSourceType.TEST, projectPath.resolve("src/test/java").toString())
        })
        addChild(createContentRoot(projectPath.resolve("src/test/resources")).apply {
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, projectPath.resolve("src/test/resources").toString())
        })
        addChild(createContentRoot(projectPath.resolve("build/generated/sources/annotationProcessor/java/test")).apply {
          data.storePath(ExternalSystemSourceType.TEST_GENERATED, projectPath.resolve("build/generated/sources/annotationProcessor/java/test").toString())
        })
      })
    }

    GradleProjectResolver.mergeSourceSetContentRootsInModulePerSourceSetMode(resolverContext, mapOf(projectModel to actualModuleNode))

    assertModuleNodeEquals(expectedModuleNode, actualModuleNode)
  }

  @Test
  fun `test content root merging for external sources`() {
    val rootPath = Path.of("path/to/root")
    val projectModel = createProjectModel(rootPath.resolve("projectRoot"), ":")
    val externalProject = createExternalProject(rootPath.resolve("projectRoot"))
    val resolverContext = createResolveContext(projectModel to externalProject)

    val expectedModuleNode = createModuleNode("project").apply {
      addChild(createSourceSetNode("main").apply {
        addChild(createContentRoot(rootPath.resolve("projectRoot/src/main")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE, rootPath.resolve("projectRoot/src/main/java").toString())
          data.storePath(ExternalSystemSourceType.RESOURCE, rootPath.resolve("projectRoot/src/main/resources").toString())
        })
        addChild(createContentRoot(rootPath.resolve("externalRoot/src/main")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE, rootPath.resolve("externalRoot/src/main/java").toString())
          data.storePath(ExternalSystemSourceType.RESOURCE, rootPath.resolve("externalRoot/src/main/resources").toString())
        })
      })
      addChild(createSourceSetNode("test").apply {
        addChild(createContentRoot(rootPath.resolve("projectRoot/src/test")).apply {
          data.storePath(ExternalSystemSourceType.TEST, rootPath.resolve("projectRoot/src/test/java").toString())
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, rootPath.resolve("projectRoot/src/test/resources").toString())
        })
        addChild(createContentRoot(rootPath.resolve("externalRoot/src/test")).apply {
          data.storePath(ExternalSystemSourceType.TEST, rootPath.resolve("externalRoot/src/test/java").toString())
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, rootPath.resolve("externalRoot/src/test/resources").toString())
        })
      })
    }

    val actualModuleNode = createModuleNode("project").apply {
      addChild(createSourceSetNode("main").apply {
        addChild(createContentRoot(rootPath.resolve("projectRoot/src/main/java")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE, rootPath.resolve("projectRoot/src/main/java").toString())
        })
        addChild(createContentRoot(rootPath.resolve("projectRoot/src/main/resources")).apply {
          data.storePath(ExternalSystemSourceType.RESOURCE, rootPath.resolve("projectRoot/src/main/resources").toString())
        })
        addChild(createContentRoot(rootPath.resolve("externalRoot/src/main/java")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE, rootPath.resolve("externalRoot/src/main/java").toString())
        })
        addChild(createContentRoot(rootPath.resolve("externalRoot/src/main/resources")).apply {
          data.storePath(ExternalSystemSourceType.RESOURCE, rootPath.resolve("externalRoot/src/main/resources").toString())
        })
      })
      addChild(createSourceSetNode("test").apply {
        addChild(createContentRoot(rootPath.resolve("projectRoot/src/test/java")).apply {
          data.storePath(ExternalSystemSourceType.TEST, rootPath.resolve("projectRoot/src/test/java").toString())
        })
        addChild(createContentRoot(rootPath.resolve("projectRoot/src/test/resources")).apply {
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, rootPath.resolve("projectRoot/src/test/resources").toString())
        })
        addChild(createContentRoot(rootPath.resolve("externalRoot/src/test/java")).apply {
          data.storePath(ExternalSystemSourceType.TEST, rootPath.resolve("externalRoot/src/test/java").toString())
        })
        addChild(createContentRoot(rootPath.resolve("externalRoot/src/test/resources")).apply {
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, rootPath.resolve("externalRoot/src/test/resources").toString())
        })
      })
    }

    GradleProjectResolver.mergeSourceSetContentRootsInModulePerSourceSetMode(resolverContext, mapOf(projectModel to actualModuleNode))

    assertModuleNodeEquals(expectedModuleNode, actualModuleNode)
  }

  @Test
  fun `test content root merging for sources in multi-module project`() {
    val projectPath = Path.of("path/to/project")
    val rootProjectModel = createProjectModel(projectPath, ":")
    val projectModel = createProjectModel(projectPath.resolve("module"), ":")
    val rootExternalProject = createExternalProject(projectPath)
    val externalProject = createExternalProject(projectPath.resolve("module"))
    val resolverContext = createResolveContext(
      rootProjectModel to rootExternalProject,
      projectModel to externalProject,
    )

    val expectedRootModuleNode = createModuleNode("project").apply {
      addChild(createSourceSetNode("main").apply {
        addChild(createContentRoot(projectPath.resolve("src/main")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE, projectPath.resolve("src/main/java").toString())
          data.storePath(ExternalSystemSourceType.RESOURCE, projectPath.resolve("src/main/resources").toString())
        })
      })
      addChild(createSourceSetNode("test").apply {
        addChild(createContentRoot(projectPath.resolve("src/test")).apply {
          data.storePath(ExternalSystemSourceType.TEST, projectPath.resolve("src/test/java").toString())
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, projectPath.resolve("src/test/resources").toString())
        })
      })
    }
    val expectedModuleNode = createModuleNode("module").apply {
      addChild(createSourceSetNode("main").apply {
        addChild(createContentRoot(projectPath.resolve("module/src/main")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE, projectPath.resolve("module/src/main/java").toString())
          data.storePath(ExternalSystemSourceType.RESOURCE, projectPath.resolve("module/src/main/resources").toString())
        })
      })
      addChild(createSourceSetNode("test").apply {
        addChild(createContentRoot(projectPath.resolve("module/src/test")).apply {
          data.storePath(ExternalSystemSourceType.TEST, projectPath.resolve("module/src/test/java").toString())
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, projectPath.resolve("module/src/test/resources").toString())
        })
      })
    }

    val actualRootModuleNode = createModuleNode("project").apply {
      addChild(createSourceSetNode("main").apply {
        addChild(createContentRoot(projectPath.resolve("src/main/java")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE, projectPath.resolve("src/main/java").toString())
        })
        addChild(createContentRoot(projectPath.resolve("src/main/resources")).apply {
          data.storePath(ExternalSystemSourceType.RESOURCE, projectPath.resolve("src/main/resources").toString())
        })
      })
      addChild(createSourceSetNode("test").apply {
        addChild(createContentRoot(projectPath.resolve("src/test/java")).apply {
          data.storePath(ExternalSystemSourceType.TEST, projectPath.resolve("src/test/java").toString())
        })
        addChild(createContentRoot(projectPath.resolve("src/test/resources")).apply {
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, projectPath.resolve("src/test/resources").toString())
        })
      })
    }
    val actualModuleNode = createModuleNode("module").apply {
      addChild(createSourceSetNode("main").apply {
        addChild(createContentRoot(projectPath.resolve("module/src/main/java")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE, projectPath.resolve("module/src/main/java").toString())
        })
        addChild(createContentRoot(projectPath.resolve("module/src/main/resources")).apply {
          data.storePath(ExternalSystemSourceType.RESOURCE, projectPath.resolve("module/src/main/resources").toString())
        })
      })
      addChild(createSourceSetNode("test").apply {
        addChild(createContentRoot(projectPath.resolve("module/src/test/java")).apply {
          data.storePath(ExternalSystemSourceType.TEST, projectPath.resolve("module/src/test/java").toString())
        })
        addChild(createContentRoot(projectPath.resolve("module/src/test/resources")).apply {
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, projectPath.resolve("module/src/test/resources").toString())
        })
      })
    }

    GradleProjectResolver.mergeSourceSetContentRootsInModulePerSourceSetMode(resolverContext, mapOf(
      rootProjectModel to actualRootModuleNode,
      projectModel to actualModuleNode
    ))

    assertModuleNodeEquals(expectedRootModuleNode, actualRootModuleNode)
    assertModuleNodeEquals(expectedModuleNode, actualModuleNode)
  }

  @Test
  fun `test content root merging for non-standard sources in android project`() {
    val rootPath = Path.of("path/to/root")
    val applicationProjectModel = createProjectModel(rootPath.resolve("application"), ":")
    val appProjectModel = createProjectModel(rootPath.resolve("application/app"), ":app")
    val applicationExternalProject = createExternalProject(rootPath.resolve("application"))
    val appExternalProject = createExternalProject(rootPath.resolve("application/app"))
    val resolverContext = createResolveContext(
      applicationProjectModel to applicationExternalProject,
      appProjectModel to appExternalProject
    )

    val expectedApplicationModuleNode = createModuleNode("application")
    val expectedAppModuleNode = createModuleNode("application.app").apply {
      addChild(createSourceSetNode("main").apply {
        addChild(createContentRoot(rootPath.resolve("externalManifest")))
        addChild(createContentRoot(rootPath.resolve("externalRoot/main")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE, rootPath.resolve("externalRoot/main/java").toString())
          data.storePath(ExternalSystemSourceType.SOURCE, rootPath.resolve("externalRoot/main/kotlin").toString())
          data.storePath(ExternalSystemSourceType.SOURCE, rootPath.resolve("externalRoot/main/shaders").toString())
          data.storePath(ExternalSystemSourceType.RESOURCE, rootPath.resolve("externalRoot/main/res").toString())
          data.storePath(ExternalSystemSourceType.RESOURCE, rootPath.resolve("externalRoot/main/assets").toString())
          data.storePath(ExternalSystemSourceType.RESOURCE, rootPath.resolve("externalRoot/main/baselineProfiles").toString())
          data.storePath(ExternalSystemSourceType.RESOURCE, rootPath.resolve("externalRoot/main/resources").toString())
        })
        addChild(createContentRoot(rootPath.resolve("application/app/src/debug")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE, rootPath.resolve("application/app/src/debug/java").toString())
          data.storePath(ExternalSystemSourceType.SOURCE, rootPath.resolve("application/app/src/debug/kotlin").toString())
          data.storePath(ExternalSystemSourceType.SOURCE, rootPath.resolve("application/app/src/debug/shaders").toString())
          data.storePath(ExternalSystemSourceType.RESOURCE, rootPath.resolve("application/app/src/debug/assets").toString())
          data.storePath(ExternalSystemSourceType.RESOURCE, rootPath.resolve("application/app/src/debug/baselineProfiles").toString())
          data.storePath(ExternalSystemSourceType.RESOURCE, rootPath.resolve("application/app/src/debug/res").toString())
          data.storePath(ExternalSystemSourceType.RESOURCE, rootPath.resolve("application/app/src/debug/resources").toString())
        })
        addChild(createContentRoot(rootPath.resolve("application/app/build/generated/ap_generated_sources/debug/out")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE_GENERATED, rootPath.resolve("application/app/build/generated/ap_generated_sources/debug/out").toString())
        })
        addChild(createContentRoot(rootPath.resolve("application/app/build/generated/res/resValues/debug")).apply {
          data.storePath(ExternalSystemSourceType.RESOURCE_GENERATED, rootPath.resolve("application/app/build/generated/res/resValues/debug").toString())
        })
      })
      addChild(createSourceSetNode("unitTest").apply {
        addChild(createContentRoot(rootPath.resolve("application/app/src/test")).apply {
          data.storePath(ExternalSystemSourceType.TEST, rootPath.resolve("application/app/src/test/java").toString())
          data.storePath(ExternalSystemSourceType.TEST, rootPath.resolve("application/app/src/test/kotlin").toString())
          data.storePath(ExternalSystemSourceType.TEST, rootPath.resolve("application/app/src/test/shaders").toString())
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, rootPath.resolve("application/app/src/test/assets").toString())
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, rootPath.resolve("application/app/src/test/baselineProfiles").toString())
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, rootPath.resolve("application/app/src/test/res").toString())
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, rootPath.resolve("application/app/src/test/resources").toString())
        })
        addChild(createContentRoot(rootPath.resolve("application/app/src/testDebug")).apply {
          data.storePath(ExternalSystemSourceType.TEST, rootPath.resolve("application/app/src/testDebug/java").toString())
          data.storePath(ExternalSystemSourceType.TEST, rootPath.resolve("application/app/src/testDebug/kotlin").toString())
          data.storePath(ExternalSystemSourceType.TEST, rootPath.resolve("application/app/src/testDebug/shaders").toString())
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, rootPath.resolve("application/app/src/testDebug/assets").toString())
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, rootPath.resolve("application/app/src/testDebug/baselineProfiles").toString())
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, rootPath.resolve("application/app/src/testDebug/res").toString())
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, rootPath.resolve("application/app/src/testDebug/resources").toString())
        })
        addChild(createContentRoot(rootPath.resolve("application/app/build/generated/ap_generated_sources/debugUnitTest/out")).apply {
          data.storePath(ExternalSystemSourceType.TEST_GENERATED, rootPath.resolve("application/app/build/generated/ap_generated_sources/debugUnitTest/out").toString())
        })
      })
      addChild(createSourceSetNode("androidTest").apply {
        addChild(createContentRoot(rootPath.resolve("application/app/src/androidTest")).apply {
          data.storePath(ExternalSystemSourceType.TEST, rootPath.resolve("application/app/src/androidTest/java").toString())
          data.storePath(ExternalSystemSourceType.TEST, rootPath.resolve("application/app/src/androidTest/kotlin").toString())
          data.storePath(ExternalSystemSourceType.TEST, rootPath.resolve("application/app/src/androidTest/shaders").toString())
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, rootPath.resolve("application/app/src/androidTest/assets").toString())
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, rootPath.resolve("application/app/src/androidTest/baselineProfiles").toString())
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, rootPath.resolve("application/app/src/androidTest/res").toString())
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, rootPath.resolve("application/app/src/androidTest/resources").toString())
        })
        addChild(createContentRoot(rootPath.resolve("application/app/src/androidTestDebug")).apply {
          data.storePath(ExternalSystemSourceType.TEST, rootPath.resolve("application/app/src/androidTestDebug/java").toString())
          data.storePath(ExternalSystemSourceType.TEST, rootPath.resolve("application/app/src/androidTestDebug/kotlin").toString())
          data.storePath(ExternalSystemSourceType.TEST, rootPath.resolve("application/app/src/androidTestDebug/shaders").toString())
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, rootPath.resolve("application/app/src/androidTestDebug/assets").toString())
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, rootPath.resolve("application/app/src/androidTestDebug/baselineProfiles").toString())
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, rootPath.resolve("application/app/src/androidTestDebug/res").toString())
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, rootPath.resolve("application/app/src/androidTestDebug/resources").toString())
        })
        addChild(createContentRoot(rootPath.resolve("application/app/build/generated/ap_generated_sources/debugAndroidTest/out")).apply {
          data.storePath(ExternalSystemSourceType.TEST_GENERATED, rootPath.resolve("application/app/build/generated/ap_generated_sources/debugAndroidTest/out").toString())
        })
        addChild(createContentRoot(rootPath.resolve("application/app/build/generated/res/resValues/androidTest/debug")).apply {
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE_GENERATED, rootPath.resolve("application/app/build/generated/res/resValues/androidTest/debug").toString())
        })
      })
    }

    val actualApplicationModuleNode = createModuleNode("application")
    val actualAppModuleNode = createModuleNode("application.app").apply {
      addChild(createSourceSetNode("main").apply {
        addChild(createContentRoot(rootPath.resolve("externalManifest/AndroidManifest.xml")))
        addChild(createContentRoot(rootPath.resolve("externalRoot/main/resources")).apply {
          data.storePath(ExternalSystemSourceType.RESOURCE, rootPath.resolve("externalRoot/main/resources").toString())
        })
        addChild(createContentRoot(rootPath.resolve("externalRoot/main/res")).apply {
          data.storePath(ExternalSystemSourceType.RESOURCE, rootPath.resolve("externalRoot/main/res").toString())
        })
        addChild(createContentRoot(rootPath.resolve("externalRoot/main/assets")).apply {
          data.storePath(ExternalSystemSourceType.RESOURCE, rootPath.resolve("externalRoot/main/assets").toString())
        })
        addChild(createContentRoot(rootPath.resolve("externalRoot/main/baselineProfiles")).apply {
          data.storePath(ExternalSystemSourceType.RESOURCE, rootPath.resolve("externalRoot/main/baselineProfiles").toString())
        })
        addChild(createContentRoot(rootPath.resolve("externalRoot/main/java")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE, rootPath.resolve("externalRoot/main/java").toString())
        })
        addChild(createContentRoot(rootPath.resolve("externalRoot/main/kotlin")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE, rootPath.resolve("externalRoot/main/kotlin").toString())
        })
        addChild(createContentRoot(rootPath.resolve("externalRoot/main/shaders")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE, rootPath.resolve("externalRoot/main/shaders").toString())
        })

        addChild(createContentRoot(rootPath.resolve("application/app/src/debug/AndroidManifest.xml")))
        addChild(createContentRoot(rootPath.resolve("application/app/src/debug/resources")).apply {
          data.storePath(ExternalSystemSourceType.RESOURCE, rootPath.resolve("application/app/src/debug/resources").toString())
        })
        addChild(createContentRoot(rootPath.resolve("application/app/src/debug/res")).apply {
          data.storePath(ExternalSystemSourceType.RESOURCE, rootPath.resolve("application/app/src/debug/res").toString())
        })
        addChild(createContentRoot(rootPath.resolve("application/app/src/debug/assets")).apply {
          data.storePath(ExternalSystemSourceType.RESOURCE, rootPath.resolve("application/app/src/debug/assets").toString())
        })
        addChild(createContentRoot(rootPath.resolve("application/app/src/debug/baselineProfiles")).apply {
          data.storePath(ExternalSystemSourceType.RESOURCE, rootPath.resolve("application/app/src/debug/baselineProfiles").toString())
        })
        addChild(createContentRoot(rootPath.resolve("application/app/src/debug/java")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE, rootPath.resolve("application/app/src/debug/java").toString())
        })
        addChild(createContentRoot(rootPath.resolve("application/app/src/debug/kotlin")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE, rootPath.resolve("application/app/src/debug/kotlin").toString())
        })
        addChild(createContentRoot(rootPath.resolve("application/app/src/debug/shaders")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE, rootPath.resolve("application/app/src/debug/shaders").toString())
        })

        addChild(createContentRoot(rootPath.resolve("application/app/build/generated/ap_generated_sources/debug/out")).apply {
          data.storePath(ExternalSystemSourceType.SOURCE_GENERATED, rootPath.resolve("application/app/build/generated/ap_generated_sources/debug/out").toString())
        })
        addChild(createContentRoot(rootPath.resolve("application/app/build/generated/res/resValues/debug")).apply {
          data.storePath(ExternalSystemSourceType.RESOURCE_GENERATED, rootPath.resolve("application/app/build/generated/res/resValues/debug").toString())
        })
      })
      addChild(createSourceSetNode("unitTest").apply {
        addChild(createContentRoot(rootPath.resolve("application/app/src/test/AndroidManifest.xml")))
        addChild(createContentRoot(rootPath.resolve("application/app/src/test/resources")).apply {
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, rootPath.resolve("application/app/src/test/resources").toString())
        })
        addChild(createContentRoot(rootPath.resolve("application/app/src/test/res")).apply {
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, rootPath.resolve("application/app/src/test/res").toString())
        })
        addChild(createContentRoot(rootPath.resolve("application/app/src/test/assets")).apply {
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, rootPath.resolve("application/app/src/test/assets").toString())
        })
        addChild(createContentRoot(rootPath.resolve("application/app/src/test/baselineProfiles")).apply {
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, rootPath.resolve("application/app/src/test/baselineProfiles").toString())
        })
        addChild(createContentRoot(rootPath.resolve("application/app/src/test/java")).apply {
          data.storePath(ExternalSystemSourceType.TEST, rootPath.resolve("application/app/src/test/java").toString())
        })
        addChild(createContentRoot(rootPath.resolve("application/app/src/test/kotlin")).apply {
          data.storePath(ExternalSystemSourceType.TEST, rootPath.resolve("application/app/src/test/kotlin").toString())
        })
        addChild(createContentRoot(rootPath.resolve("application/app/src/test/shaders")).apply {
          data.storePath(ExternalSystemSourceType.TEST, rootPath.resolve("application/app/src/test/shaders").toString())
        })

        addChild(createContentRoot(rootPath.resolve("application/app/src/testDebug/AndroidManifest.xml")))
        addChild(createContentRoot(rootPath.resolve("application/app/src/testDebug/resources")).apply {
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, rootPath.resolve("application/app/src/testDebug/resources").toString())
        })
        addChild(createContentRoot(rootPath.resolve("application/app/src/testDebug/res")).apply {
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, rootPath.resolve("application/app/src/testDebug/res").toString())
        })
        addChild(createContentRoot(rootPath.resolve("application/app/src/testDebug/assets")).apply {
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, rootPath.resolve("application/app/src/testDebug/assets").toString())
        })
        addChild(createContentRoot(rootPath.resolve("application/app/src/testDebug/baselineProfiles")).apply {
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, rootPath.resolve("application/app/src/testDebug/baselineProfiles").toString())
        })
        addChild(createContentRoot(rootPath.resolve("application/app/src/testDebug/java")).apply {
          data.storePath(ExternalSystemSourceType.TEST, rootPath.resolve("application/app/src/testDebug/java").toString())
        })
        addChild(createContentRoot(rootPath.resolve("application/app/src/testDebug/kotlin")).apply {
          data.storePath(ExternalSystemSourceType.TEST, rootPath.resolve("application/app/src/testDebug/kotlin").toString())
        })
        addChild(createContentRoot(rootPath.resolve("application/app/src/testDebug/shaders")).apply {
          data.storePath(ExternalSystemSourceType.TEST, rootPath.resolve("application/app/src/testDebug/shaders").toString())
        })

        addChild(createContentRoot(rootPath.resolve("application/app/build/generated/ap_generated_sources/debugUnitTest/out")).apply {
          data.storePath(ExternalSystemSourceType.TEST_GENERATED, rootPath.resolve("application/app/build/generated/ap_generated_sources/debugUnitTest/out").toString())
        })
      })
      addChild(createSourceSetNode("androidTest").apply {
        addChild(createContentRoot(rootPath.resolve("application/app/src/androidTest/AndroidManifest.xml")))
        addChild(createContentRoot(rootPath.resolve("application/app/src/androidTest/resources")).apply {
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, rootPath.resolve("application/app/src/androidTest/resources").toString())
        })
        addChild(createContentRoot(rootPath.resolve("application/app/src/androidTest/res")).apply {
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, rootPath.resolve("application/app/src/androidTest/res").toString())
        })
        addChild(createContentRoot(rootPath.resolve("application/app/src/androidTest/assets")).apply {
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, rootPath.resolve("application/app/src/androidTest/assets").toString())
        })
        addChild(createContentRoot(rootPath.resolve("application/app/src/androidTest/baselineProfiles")).apply {
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, rootPath.resolve("application/app/src/androidTest/baselineProfiles").toString())
        })
        addChild(createContentRoot(rootPath.resolve("application/app/src/androidTest/java")).apply {
          data.storePath(ExternalSystemSourceType.TEST, rootPath.resolve("application/app/src/androidTest/java").toString())
        })
        addChild(createContentRoot(rootPath.resolve("application/app/src/androidTest/kotlin")).apply {
          data.storePath(ExternalSystemSourceType.TEST, rootPath.resolve("application/app/src/androidTest/kotlin").toString())
        })
        addChild(createContentRoot(rootPath.resolve("application/app/src/androidTest/shaders")).apply {
          data.storePath(ExternalSystemSourceType.TEST, rootPath.resolve("application/app/src/androidTest/shaders").toString())
        })

        addChild(createContentRoot(rootPath.resolve("application/app/src/androidTestDebug/AndroidManifest.xml")))
        addChild(createContentRoot(rootPath.resolve("application/app/src/androidTestDebug/resources")).apply {
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, rootPath.resolve("application/app/src/androidTestDebug/resources").toString())
        })
        addChild(createContentRoot(rootPath.resolve("application/app/src/androidTestDebug/res")).apply {
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, rootPath.resolve("application/app/src/androidTestDebug/res").toString())
        })
        addChild(createContentRoot(rootPath.resolve("application/app/src/androidTestDebug/assets")).apply {
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, rootPath.resolve("application/app/src/androidTestDebug/assets").toString())
        })
        addChild(createContentRoot(rootPath.resolve("application/app/src/androidTestDebug/baselineProfiles")).apply {
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE, rootPath.resolve("application/app/src/androidTestDebug/baselineProfiles").toString())
        })
        addChild(createContentRoot(rootPath.resolve("application/app/src/androidTestDebug/java")).apply {
          data.storePath(ExternalSystemSourceType.TEST, rootPath.resolve("application/app/src/androidTestDebug/java").toString())
        })
        addChild(createContentRoot(rootPath.resolve("application/app/src/androidTestDebug/kotlin")).apply {
          data.storePath(ExternalSystemSourceType.TEST, rootPath.resolve("application/app/src/androidTestDebug/kotlin").toString())
        })
        addChild(createContentRoot(rootPath.resolve("application/app/src/androidTestDebug/shaders")).apply {
          data.storePath(ExternalSystemSourceType.TEST, rootPath.resolve("application/app/src/androidTestDebug/shaders").toString())
        })

        addChild(createContentRoot(rootPath.resolve("application/app/build/generated/ap_generated_sources/debugAndroidTest/out")).apply {
          data.storePath(ExternalSystemSourceType.TEST_GENERATED, rootPath.resolve("application/app/build/generated/ap_generated_sources/debugAndroidTest/out").toString())
        })
        addChild(createContentRoot(rootPath.resolve("application/app/build/generated/res/resValues/androidTest/debug")).apply {
          data.storePath(ExternalSystemSourceType.TEST_RESOURCE_GENERATED, rootPath.resolve("application/app/build/generated/res/resValues/androidTest/debug").toString())
        })
      })
    }

    GradleProjectResolver.mergeSourceSetContentRootsInModulePerSourceSetMode(resolverContext, mapOf(
      applicationProjectModel to actualApplicationModuleNode,
      appProjectModel to actualAppModuleNode
    ))

    assertModuleNodeEquals(expectedApplicationModuleNode, actualApplicationModuleNode)
    assertModuleNodeEquals(expectedAppModuleNode, actualAppModuleNode)
  }
}