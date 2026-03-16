// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections.eel

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.project.IntelliJProjectConfiguration
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightPlatformTestCase.closeAndDeleteProject
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.utils.vfs.refreshAndGetVirtualDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.devkit.inspections.eel.UseOptimizedEelFunctions
import org.jetbrains.kotlin.idea.test.UseK2PluginMode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.nio.file.Path

@Suppress("ClassName", "TestFunctionName")
@UseK2PluginMode
@TestApplication
class UseOptimizedEelFunctionsTest {
  private lateinit var myFixture: JavaCodeInsightTestFixture

  @Nested
  inner class `handling different ways to write the same method` {
    @Nested
    inner class `class dot method` {
      @Test
      fun Java() {
        @Language("Java")
        val source = """
          import java.io.IOException;
          import java.nio.file.Files;
          import java.nio.file.Path;
    
          class Example {
            void example() throws IOException {
              byte[] result = Files.<warning descr="Works ineffectively with remote Eel">readAllBytes</warning>(Path.of("hello.txt"));
            }
          }
        """.trimIndent()

        @Language("Java")
        val expectedResult = """
          import com.intellij.platform.eel.fs.EelFiles;
          
          import java.io.IOException;
          import java.nio.file.Files;
          import java.nio.file.Path;
    
          class Example {
            void example() throws IOException {
              byte[] result = EelFiles.readAllBytes(Path.of("hello.txt"));
            }
          }
        """.trimIndent()

        doTest("Example.java", source, expectedResult)
      }

      @Test
      fun Kotlin() {
        @Language("Kt")
        val source = """
          import java.nio.file.Files
          import java.nio.file.Path
    
          fun example() {
            val result = Files.<warning descr="Works ineffectively with remote Eel">readAllBytes</warning>(Path.of("hello.txt"))
          }
        """.trimIndent()

        @Language("Kt")
        val expectedResult = """
          import com.intellij.platform.eel.fs.EelFiles
          import java.nio.file.Files
          import java.nio.file.Path
    
          fun example() {
            val result = EelFiles.readAllBytes(Path.of("hello.txt"))
          }
        """.trimIndent()

        doTest("Example.kt", source, expectedResult)
      }
    }

    @Nested
    inner class `usages in docs are ignored` {
      @Test
      fun Java() {
        @Language("Java")
        val source = """
          import java.io.IOException;
          import java.nio.file.Files;
          import java.nio.file.Path;
    
          class Example {
            /**
             * <p>{@link java.nio.file.Files#readAllBytes}</p>
             * <p>{@link java.nio.file.Files#readAllBytes(Path)}</p>
             * <p>{@link java.nio.file.Files#readAllBytes(Path) Files.readAllBytes(Path)}</p>
             */
            void example() {}
          }
        """.trimIndent()

        doTest("Example.java", source)
      }

      @Test
      fun Kotlin() {
        @Language("Kt")
        val source = """
          import java.nio.file.Files
          import java.nio.file.Path
    
          /**
           * [java.nio.file.Files.readAllBytes]
           * 
           * [java.nio.file.Files.readAllBytes(Path)]
           *
           * [java.nio.file.Files.readAllBytes(Path) Files.readAllBytes(Path)]
           */
          fun example() = Unit
        """.trimIndent()

        doTest("Example.kt", source)
      }

      private fun doTest(fileName: String, source: String) = timeoutRunBlocking {
        val exampleFile = myFixture.configureByText(fileName, source)
        withContext(Dispatchers.EDT) {
          myFixture.openFileInEditor(exampleFile.virtualFile)
        }

        myFixture.testHighlighting()
      }
    }

    @Nested
    inner class `class dot method inside another call` {
      @Test
      fun Java() {
        @Language("Java")
        val source = """
          import java.io.IOException;
          import java.nio.ByteBuffer;
          import java.nio.file.Files;
          import java.nio.file.Path;
    
          class Example {
            void example() throws IOException {
              ByteBuffer result = ByteBuffer.wrap(Files.<warning descr="Works ineffectively with remote Eel">readAllBytes</warning>(Path.of("hello.txt")));
            }
          }
        """.trimIndent()

        @Language("Java")
        val expectedResult = """
          import com.intellij.platform.eel.fs.EelFiles;
          
          import java.io.IOException;
          import java.nio.ByteBuffer;
          import java.nio.file.Files;
          import java.nio.file.Path;
    
          class Example {
            void example() throws IOException {
              ByteBuffer result = ByteBuffer.wrap(EelFiles.readAllBytes(Path.of("hello.txt")));
            }
          }
        """.trimIndent()

        doTest("Example.java", source, expectedResult)
      }

      @Test
      fun Kotlin() {
        @Language("Kt")
        val source = """
          import java.nio.ByteBuffer
          import java.nio.file.Files
          import java.nio.file.Path
    
          fun example() {
            val result = ByteBuffer.wrap(Files.<warning descr="Works ineffectively with remote Eel">readAllBytes</warning>(Path.of("hello.txt")))
          }
        """.trimIndent()

        @Language("Kt")
        val expectedResult = """
          import com.intellij.platform.eel.fs.EelFiles
          import java.nio.ByteBuffer
          import java.nio.file.Files
          import java.nio.file.Path
    
          fun example() {
            val result = ByteBuffer.wrap(EelFiles.readAllBytes(Path.of("hello.txt")))
          }
        """.trimIndent()

        doTest("Example.kt", source, expectedResult)
      }
    }

    @Nested
    inner class `class dot method in call chain` {
      @Test
      fun Java() {
        @Language("Java")
        val source = """
          import java.io.IOException;
          import java.nio.ByteBuffer;
          import java.nio.file.Files;
          import java.nio.file.Path;
    
          class Example {
            void example() throws IOException {
              int hash = Files.<warning descr="Works ineffectively with remote Eel">readAllBytes</warning>(Path.of("hello.txt")).hashCode();
            }
          }
        """.trimIndent()

        @Language("Java")
        val expectedResult = """
          import com.intellij.platform.eel.fs.EelFiles;
          
          import java.io.IOException;
          import java.nio.ByteBuffer;
          import java.nio.file.Files;
          import java.nio.file.Path;
    
          class Example {
            void example() throws IOException {
              int hash = EelFiles.readAllBytes(Path.of("hello.txt")).hashCode();
            }
          }
        """.trimIndent()

        doTest("Example.java", source, expectedResult)
      }

      @Test
      fun Kotlin() {
        @Language("Kt")
        val source = """
          import java.nio.ByteBuffer
          import java.nio.file.Files
          import java.nio.file.Path
    
          fun example() {
            val hash = Files.<warning descr="Works ineffectively with remote Eel">readAllBytes</warning>(Path.of("hello.txt")).hashCode()
          }
        """.trimIndent()

        @Language("Kt")
        val expectedResult = """
          import com.intellij.platform.eel.fs.EelFiles
          import java.nio.ByteBuffer
          import java.nio.file.Files
          import java.nio.file.Path
    
          fun example() {
            val hash = EelFiles.readAllBytes(Path.of("hello.txt")).hashCode()
          }
        """.trimIndent()

        doTest("Example.kt", source, expectedResult)
      }
    }

    @Nested
    inner class `class dot method and wildcard import` {
      @Test
      fun Java() {
        @Language("Java")
        val source = """
          import java.io.IOException;
          import java.nio.file.*;
    
          class Example {
            void example() throws IOException {
              byte[] result = Files.<warning descr="Works ineffectively with remote Eel">readAllBytes</warning>(Path.of("hello.txt"));
            }
          }
        """.trimIndent()

        @Language("Java")
        val expectedResult = """
          import com.intellij.platform.eel.fs.EelFiles;
          
          import java.io.IOException;
          import java.nio.file.*;
    
          class Example {
            void example() throws IOException {
              byte[] result = EelFiles.readAllBytes(Path.of("hello.txt"));
            }
          }
        """.trimIndent()

        doTest("Example.java", source, expectedResult)
      }

      @Test
      fun Kotlin() {
        @Language("Kt")
        val source = """
          import java.nio.file.*
    
          fun example() {
            val result = Files.<warning descr="Works ineffectively with remote Eel">readAllBytes</warning>(Path.of("hello.txt"))
          }
        """.trimIndent()

        @Language("Kt")
        val expectedResult = """
          import com.intellij.platform.eel.fs.EelFiles
          import java.nio.file.*
    
          fun example() {
            val result = EelFiles.readAllBytes(Path.of("hello.txt"))
          }
        """.trimIndent()

        doTest("Example.kt", source, expectedResult)
      }
    }

    @Nested
    inner class `fully qualified class dot method` {
      @Test
      fun Java() {
        @Language("Java")
        val source = """
          import java.io.IOException;
    
          class Example {
            void example() throws IOException {
              var result = java.nio.file.Files.<warning descr="Works ineffectively with remote Eel">readAllBytes</warning>(java.nio.file.Path.of("hello.txt"));
            }
          }
        """.trimIndent()

        // It would be better to keep FQN for java.nio.file.Path, but it's impossible after the commit b739467b7a9728dd1e7eabe814dda073e66ad563
        @Language("Java")
        val expectedResult = """
          import com.intellij.platform.eel.fs.EelFiles;
          
          import java.io.IOException;
          import java.nio.file.Path;
    
          class Example {
            void example() throws IOException {
              var result = EelFiles.readAllBytes(Path.of("hello.txt"));
            }
          }
        """.trimIndent()

        doTest("Example.java", source, expectedResult)
      }

      @Test
      fun Kotlin() {
        @Language("Kt")
        val source = """
          import java.nio.file.Files
    
          fun example() {
            val result = java.nio.file.Files.<warning descr="Works ineffectively with remote Eel">readAllBytes</warning>(java.nio.file.Path.of("hello.txt"))
          }
        """.trimIndent()

        // It would be better to keep FQN for java.nio.file.Path, but it's impossible after the commit b739467b7a9728dd1e7eabe814dda073e66ad563
        @Language("Kt")
        val expectedResult = """
          import com.intellij.platform.eel.fs.EelFiles
          import java.nio.file.Files
          import java.nio.file.Path
    
          fun example() {
            val result = EelFiles.readAllBytes(Path.of("hello.txt"))
          }
        """.trimIndent()

        doTest("Example.kt", source, expectedResult)
      }
    }

    @Nested
    inner class `static import` {
      @Test
      fun Java() {
        @Language("Java")
        val source = """
          import java.io.IOException;
          import static java.nio.file.Files.readAllBytes;
          import java.nio.file.Path;
    
          class Example {
            void example() throws IOException {
              var result = <warning descr="Works ineffectively with remote Eel">readAllBytes</warning>(Path.of("hello.txt"));
            }
          }
        """.trimIndent()

        @Language("Java")
        val expectedResult = """
          import com.intellij.platform.eel.fs.EelFiles;
          
          import java.io.IOException;
          import static java.nio.file.Files.readAllBytes;
          import java.nio.file.Path;
    
          class Example {
            void example() throws IOException {
              var result = EelFiles.readAllBytes(Path.of("hello.txt"));
            }
          }
        """.trimIndent()

        doTest("Example.java", source, expectedResult)
      }

      @Test
      fun Kotlin() {
        @Language("Kt")
        val source = """
          import java.nio.file.Files.readAllBytes
          import java.nio.file.Path
    
          fun example() {
            val result = <warning descr="Works ineffectively with remote Eel">readAllBytes</warning>(Path.of("hello.txt"))
          }
        """.trimIndent()

        @Language("Kt")
        val expectedResult = """
          import com.intellij.platform.eel.fs.EelFiles
          import java.nio.file.Files.readAllBytes
          import java.nio.file.Path
    
          fun example() {
            val result = EelFiles.readAllBytes(Path.of("hello.txt"))
          }
        """.trimIndent()

        doTest("Example.kt", source, expectedResult)
      }
    }

    @Test
    fun `static import with alias Kotlin`() {
      @Language("Kt")
      val source = """
        import java.nio.file.Files.readAllBytes as foobar
        import java.nio.file.Path
  
        fun example() {
          val result = <warning descr="Works ineffectively with remote Eel">foobar</warning>(Path.of("hello.txt"))
        }
      """.trimIndent()

      @Language("Kt")
      val expectedResult = """
        import com.intellij.platform.eel.fs.EelFiles
        import java.nio.file.Files.readAllBytes as foobar
        import java.nio.file.Path
  
        fun example() {
          val result = EelFiles.readAllBytes(Path.of("hello.txt"))
        }
      """.trimIndent()

      doTest("Example.kt", source, expectedResult)
    }
  }

  @Nested
  inner class `smoke tests for specific methods` {
    @Test
    fun `Files readAllBytes`() {
      @Language("Java")
      val source = """
        import java.io.IOException;
        import java.nio.file.Files;
        import java.nio.file.Path;
  
        class Example {
          void example() throws IOException {
            byte[] result = Files.<warning descr="Works ineffectively with remote Eel">readAllBytes</warning>(Path.of("hello.txt"));
          }
        }
      """.trimIndent()

      @Language("Java")
      val expectedResult = """
        import com.intellij.platform.eel.fs.EelFiles;
        
        import java.io.IOException;
        import java.nio.file.Files;
        import java.nio.file.Path;
  
        class Example {
          void example() throws IOException {
            byte[] result = EelFiles.readAllBytes(Path.of("hello.txt"));
          }
        }
      """.trimIndent()

      doTest("Example.java", source, expectedResult)
    }

    @Test
    fun `Files readString`() {
      @Language("Java")
      val source = """
        import java.io.IOException;
        import java.nio.charset.StandardCharsets;
        import java.nio.file.Files;
        import java.nio.file.Path;
  
        class Example {
          void example() throws IOException {
            String a = Files.<warning descr="Works ineffectively with remote Eel">readString</warning>(Path.of("hello.txt"));
            String b = Files.<warning descr="Works ineffectively with remote Eel">readString</warning>(Path.of("hello.txt"), StandardCharsets.UTF_8);
          }
        }
      """.trimIndent()

      @Language("Java")
      val expectedResult = """
        import com.intellij.platform.eel.fs.EelFiles;
        
        import java.io.IOException;
        import java.nio.charset.StandardCharsets;
        import java.nio.file.Files;
        import java.nio.file.Path;
  
        class Example {
          void example() throws IOException {
            String a = EelFiles.readString(Path.of("hello.txt"));
            String b = EelFiles.readString(Path.of("hello.txt"), StandardCharsets.UTF_8);
          }
        }
      """.trimIndent()

      doTest("Example.java", source, expectedResult)
    }

    @Test
    fun deleteRecursively() {
      @Suppress("unused")
      fun someFnThatIsNeverCalled() {
        // Just a reminder that these functions are checked by this inspection.
        // If something happens with the functions, the inspection is to be modified as well.
        fun path(): Path = error("oops")
        com.intellij.openapi.util.io.NioFiles.deleteRecursively(path())
        com.intellij.openapi.util.io.FileUtilRt.deleteRecursively(path())
      }

      @Language("Java")
      val source = """
        import com.intellij.openapi.util.io.NioFiles;
        import com.intellij.openapi.util.io.FileUtilRt;
        import java.io.IOException;
        import java.nio.charset.StandardCharsets;
        import java.nio.file.Files;
        import java.nio.file.Path;
  
        class Example {
          void example() throws IOException {
            NioFiles.<warning descr="Works ineffectively with remote Eel">deleteRecursively</warning>(Path.of(""));
            NioFiles.deleteRecursively(Path.of(""), path -> {});  // This overload has no replacement in eel.
            
            FileUtilRt.<warning descr="Works ineffectively with remote Eel">deleteRecursively</warning>(Path.of(""));
          }
        }
      """.trimIndent()

      @Language("Java")
      val expectedResult = """
        import com.intellij.openapi.util.io.NioFiles;
        import com.intellij.openapi.util.io.FileUtilRt;
        import com.intellij.platform.eel.fs.EelFileUtils;
        
        import java.io.IOException;
        import java.nio.charset.StandardCharsets;
        import java.nio.file.Files;
        import java.nio.file.Path;
  
        class Example {
          void example() throws IOException {
            EelFileUtils.deleteRecursively(Path.of(""));
            NioFiles.deleteRecursively(Path.of(""), path -> {});  // This overload has no replacement in eel.
            
            EelFileUtils.deleteRecursively(Path.of(""));
          }
        }
      """.trimIndent()

      doTest("Example.java", source, expectedResult)
    }
  }

  @BeforeEach
  fun initInspection() {
    val fixture = IdeaTestFixtureFactory
      .getFixtureFactory()
      .createLightFixtureBuilder(getProjectDescriptor(), UseOptimizedEelFunctionsTest::class.simpleName!!)
      .getFixture()
    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(fixture, LightTempDirTestFixtureImpl(true))
    myFixture.setUp()
    myFixture.enableInspections(UseOptimizedEelFunctions::class.java)
  }

  private fun doTest(fileName: String, source: String, expectedResult: String) = timeoutRunBlocking {
    val exampleFile = myFixture.configureByText(fileName, source)
    withContext(Dispatchers.EDT) {
      myFixture.openFileInEditor(exampleFile.virtualFile)
    }

    myFixture.testHighlighting()

    withContext(Dispatchers.EDT) {
      for (quickFix in myFixture.getAllQuickFixes().filter { it.familyName == "Replace it with Eel API" }) {
        myFixture.launchAction(quickFix)
      }
    }

    myFixture.checkResult(expectedResult)
  }

  @AfterEach
  fun tearDownFixture() {
    if (::myFixture.isInitialized) {
      myFixture.tearDown()
    }
    runBlocking(Dispatchers.EDT) {
      closeAndDeleteProject()
    }
  }

  private fun getProjectDescriptor(): LightProjectDescriptor = object : DefaultLightProjectDescriptor(IdeaTestUtil::getMockJdk21) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      val kotlinStdlibName = "kotlin-stdlib"
      val kotlinStdlibPaths = IntelliJProjectConfiguration.getProjectLibrary(kotlinStdlibName)
      val kotlinStdlib = model.moduleLibraryTable.createLibrary(kotlinStdlibName)

      kotlinStdlib.modifiableModel.apply {
        for (rootUrl in kotlinStdlibPaths.classesUrls) {
          addRoot(rootUrl, OrderRootType.CLASSES)
        }
        for (rootUrl in kotlinStdlibPaths.sourcesUrls) {
          addRoot(rootUrl, OrderRootType.SOURCES)
        }
        commit()
      }

      for (directory in listOf("platform/eel/src", "platform/util/src", "platform/util-rt/src", "platform/platform-api/src")) {
        PsiTestUtil.addSourceContentToRoots(
          module,
          Path.of(PathManager.getCommunityHomePath(), directory).refreshAndGetVirtualDirectory(),
        )
      }
    }
  }
}