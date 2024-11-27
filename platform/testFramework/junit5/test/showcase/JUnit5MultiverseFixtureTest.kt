// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@TestApplication
class JUnit5MultiverseFixtureTest {
  companion object {
    val multiverseFixture = multiverseProjectFixture("test-multiverse-project") {
      module("module1") {
        contentRoot("module1PrimaryContent") {
          sourceRoot("source1", sourceRootId = "source1ID") {
            dir("dir1") {
              file(
                "file1.java", """
                            public class A1 {
                            }
                        """.trimIndent()
              )
            }
            dir("dir2") {
              file(
                "file2.java", """
                            public class A2 {
                            }
                        """.trimIndent()
              )
            }
          }
          sourceRoot("source2", sourceRootId = "source2ID") {
            dir("extraDir") {
              file(
                "file3.txt", """
                            Key=Value
                        """.trimIndent()
              )
            }
          }
        }
        module("nestedModule1") {
          contentRoot("nestedContentRoot1") {
            sourceRoot("nestedSourceRoot1") {
              dir("nestedDir") {
                file(
                  "nestedFile.java", """
                                public class NestedClass {
                                }
                            """.trimIndent()
                )
              }
            }
          }
        }
        module("nestedModule2") {
          contentRoot("nestedModuleContent") {
            sourceRoot("nestedSourceInContent") {
              dir("docs") {
                file(
                  "README.md", """
                                # Documentation
                                This is a nested module source.
                            """.trimIndent()
                )
              }
            }
          }
        }
      }

      module("module2") {
        contentRoot("simpleContentRoot") {
          dir("dirForSource") {
            sourceRoot("simpleSourceRoot1") {
              dir("utils") {
                file(
                  "Utils.java", """
                                    public class Utils {
                                        public static void print() {
                                            System.out.println("Hello, World!");
                                        }
                                    }
                                """.trimIndent()
                )
              }
            }
            sourceRoot("simpleSourceRoot2") {
              dir("core") {
                file(
                  "Core.java", """
                                    public class Core {
                                        public static void init() {
                                            System.out.println("Core initialized.");
                                        }
                                    }
                                """.trimIndent()
                )
              }
            }
          }
        }

        module("nestedModule3") {
          contentRoot("nestedModule3ContentRoot") {
            sharedSourceRoot("source1ID")
          }
        }

        sharedContentRoot("module1PrimaryContent", "module1") {
          sharedSourceRoot("source1ID")
        }

        sharedSourceRoot("source2ID")
      }

    }
  }

  private fun printDirectoryStructure(directory: VirtualFile, indent: String) {
    if (directory.isDirectory) {
      println("$indent Directory: ${directory.name}")
      for (child in directory.children) {
        printDirectoryStructure(child, "$indent  ")
      }
    }
    else {
      println("$indent File: ${directory.name}")
    }
  }

  private fun printModuleStructure(module: Module) {
    val rootManager = module.rootManager
    for (contentEntry in rootManager.contentEntries) {
      println("Content Root: ${contentEntry.url}")
      for (sourceFolder in contentEntry.sourceFolders) {
        println("Source Folder: ${sourceFolder.url}")
        val file = sourceFolder.file ?: continue
        printDirectoryStructure(file, "    ")
      }
    }
  }

  @Test
  fun testMultiverseFixtureStructure() {
    val project = multiverseFixture.get()
    val moduleManager = ModuleManager.getInstance(project)
    val modules = moduleManager.modules
    println("Project: ${project.name}")

    for (module in modules) {
      println("Module: ${module.name}")
      printModuleStructure(module)
    }

    assert(modules.isNotEmpty()) { "Expected some modules, but none were found in the project!" }
  }

  @Test
  fun testMultiverseFixtureModuleSourceRoots() {
    val project = multiverseFixture.get()
    val moduleManager = ModuleManager.getInstance(project)

    val module1 =
      moduleManager.modules.find { it.name == "module1" } ?: error("Module1 not found")
    val module1SourceRoots = module1.rootManager.sourceRoots
    assert(module1SourceRoots.isNotEmpty()) {
      "Module1 should have source roots, but none were found"
    }

    val module2 = moduleManager.modules.find { it.name == "module2" } ?: error("Module2 not found")
    val sourceRoots = module2.rootManager.sourceRoots

    val sharedSourceRoot = sourceRoots.find { it.path.contains("source1") }
    assertNotNull(sharedSourceRoot) { "Shared Source Root 'source1' should exist in module2" }

    val uniqueSourceRoot = sourceRoots.find { it.path.contains("source2") }
    assertNotNull(uniqueSourceRoot) { "Unique Source Root 'source2' should exist in module2" }
  }

  @Test
  fun testNonexistentModule() {
    val project = multiverseFixture.get()
    val moduleManager = ModuleManager.getInstance(project)

    val nonexistentModule = moduleManager.modules.find { it.name == "nonexistent" }
    assert(nonexistentModule == null) {
      "Nonexistent module should NOT exist in the project."
    }
  }

  @Test
  fun testSourceRootContents() {
    val project = multiverseFixture.get()
    val moduleManager = ModuleManager.getInstance(project)

    val module1 = moduleManager.modules.find { it.name == "module1" }
    assertNotNull(module1, "Module1 should exist")

    val module1SourceRoots = module1!!.rootManager.sourceRoots
    val module1Dir1 = module1SourceRoots.flatMap { it.children.toList() }
      .find { it.name == "dir1" }
    assertNotNull(module1Dir1, "Directory dir1 should exist in module1")

    val module1File1 = module1Dir1!!.findChild("file1.java")
    assertNotNull(module1File1, "File file1.java should exist in dir1 of module1")

    val file1Content = module1File1!!.contentsToByteArray().decodeToString()
    assert(file1Content.contains("public class A1")) {
      "File file1.java content does not match expected content"
    }
  }

  @Test
  fun testModuleContentRoots() {
    val project = multiverseFixture.get()
    val moduleManager = ModuleManager.getInstance(project)

    val module1 = moduleManager.modules.find { it.name == "module1" }
    assertNotNull(module1, "Module1 should exist")
    val module1ContentRoots = module1!!.rootManager.contentRoots
    assert(module1ContentRoots.size == 1) {
      "Expected only 1 content root for module1, but found: ${module1ContentRoots.size}"
    }
  }

  @Test
  fun testSharedSourceRootsInModule2() {
    val project = multiverseFixture.get()
    val moduleManager = ModuleManager.getInstance(project)
    val module2 = moduleManager.modules.find { it.name == "module2" }
    assertNotNull(module2, "Module2 should exist")

    val module2SourceRoots = module2!!.rootManager.sourceRoots.map { it.toNioPath().toFile() }

    val sharedSource1 = module2SourceRoots.find { it.name == "source1" }
    assertNotNull(sharedSource1, "Shared Source Root 'source1' (source1ID) should exist in module2")

    val sharedSource2 = module2SourceRoots.find { it.name == "source2" }
    assertNotNull(sharedSource2, "Shared Source Root 'source2' (source2ID) should exist in module2")

    val file1InSource1 = sharedSource1!!.walkTopDown().find { it.name == "file1.java" }
    assertNotNull(file1InSource1, "File 'file1.java' should exist in shared source1 (source1ID)")
    assert(file1InSource1!!.readText().contains("public class A1")) {
      "File 'file1.java' content does not match expected content in shared source1"
    }

    val file3InSource2 = sharedSource2!!.walkTopDown().find { it.name == "file3.txt" }
    assertNotNull(file3InSource2, "File 'file3.txt' should exist in shared source2 (source2ID)")
    assert(file3InSource2!!.readText().contains("Key=Value")) {
      "File 'file3.txt' content does not match expected content in shared source2"
    }
  }

  @Test
  fun testAllModulesCreatedInProject() {
    val project = multiverseFixture.get()
    val moduleManager = ModuleManager.getInstance(project)

    val expectedModules = listOf("module1", "nestedModule1", "nestedModule2", "module2", "nestedModule3")

    val projectModules = moduleManager.modules.map { it.name }

    assertTrue(
      expectedModules.all { it in projectModules },
      "Some expected modules are missing. Expected: $expectedModules, Found: $projectModules"
    )

    assertEquals(expectedModules.size, projectModules.size, "Unexpected number of modules in the project")
  }

  @Test
  fun testNestedModules() {
    val project = multiverseFixture.get()
    val moduleManager = ModuleManager.getInstance(project)

    val nestedModule1 = moduleManager.modules.find { it.name == "nestedModule1" }
    assertNotNull(nestedModule1, "'nestedModule1' should exist")

    val nestedContentRoot1 = nestedModule1!!.rootManager.contentRoots.find { it.name == "nestedContentRoot1" }
    assertNotNull(nestedContentRoot1, "'nestedContentRoot1' should be the content root for nestedModule1")

    val nestedSourceRoot1 = nestedModule1.rootManager.sourceRoots.find { it.name == "nestedSourceRoot1" }
    assertNotNull(nestedSourceRoot1, "'nestedSourceRoot1' should exist in nestedModule1")

    val nestedDir = nestedSourceRoot1!!.children.find { it.name == "nestedDir" }
    assertNotNull(nestedDir, "'nestedDir' should exist in nestedModule1's source root")

    val nestedFile = nestedDir!!.findChild("nestedFile.java")
    assertNotNull(nestedFile, "'nestedFile.java' should exist in 'nestedDir'")

    val nestedFileContent = nestedFile!!.contentsToByteArray().decodeToString()
    assert(nestedFileContent.contains("public class NestedClass")) {
      "File 'nestedFile.java' content does not match expected content in nestedModule1"
    }

    val nestedModule2 = moduleManager.modules.find { it.name == "nestedModule2" }
    assertNotNull(nestedModule2, "'nestedModule2' should exist")

    val nestedContentRoot2 = nestedModule2!!.rootManager.contentRoots.find { it.name == "nestedModuleContent" }
    assertNotNull(nestedContentRoot2, "'nestedModuleContent' should be the content root for nestedModule2")

    val nestedSourceRoot2 = nestedModule2.rootManager.sourceRoots.find { it.name == "nestedSourceInContent" }
    assertNotNull(nestedSourceRoot2, "'nestedSourceInContent' should exist in nestedModule2")

    val docsDir = nestedSourceRoot2!!.children.find { it.name == "docs" }
    assertNotNull(docsDir, "'docs' directory should exist in nestedModule2's nested source root")

    val readmeFile = docsDir!!.findChild("README.md")
    assertNotNull(readmeFile, "'README.md' should exist in 'docs'")

    val readmeContent = readmeFile!!.contentsToByteArray().decodeToString()
    assert(readmeContent.contains("# Documentation")) {
      "File 'README.md' content does not match expected content in nestedModule2"
    }
  }

  @Test
  fun testNestedModuleInModule2WithSharedSource() {
    val project = multiverseFixture.get()
    val moduleManager = ModuleManager.getInstance(project)

    val nestedModule3 = moduleManager.modules.find { it.name == "nestedModule3" }
    assertNotNull(nestedModule3, "'nestedModule3' should exist in module2")

    val nestedModule3SourceRoots = nestedModule3!!.rootManager.sourceRoots
    val sharedSource1InNestedModule3 = nestedModule3SourceRoots.find { it.name == "source1" }
    assertNotNull(sharedSource1InNestedModule3, "'source1' should be accessible in nestedModule3 from module1")

    val source1Dir = sharedSource1InNestedModule3!!.children.toList()
    val sharedDir1 = source1Dir.find { it.name == "dir1" }
    assertNotNull(sharedDir1, "'dir1' should exist in the shared source1 of nestedModule3")

    val sharedFile1 = sharedDir1!!.findChild("file1.java")
    assertNotNull(sharedFile1, "'file1.java' should exist in the shared source1 in nestedModule3")
    val file1Content = sharedFile1!!.contentsToByteArray().decodeToString()
    assert(file1Content.contains("public class A1")) {
      "File 'file1.java' content in shared source1 does not match expected content"
    }

    val sharedDir2 = source1Dir.find { it.name == "dir2" }
    assertNotNull(sharedDir2, "'dir2' should exist in the shared source1 of nestedModule3")

    val sharedFile2 = sharedDir2!!.findChild("file2.java")
    assertNotNull(sharedFile2, "'file2.java' should exist in the shared source1 in nestedModule3")
    val file2Content = sharedFile2!!.contentsToByteArray().decodeToString()
    assert(file2Content.contains("public class A2")) {
      "File 'file2.java' content in shared source1 does not match expected content"
    }
  }
}