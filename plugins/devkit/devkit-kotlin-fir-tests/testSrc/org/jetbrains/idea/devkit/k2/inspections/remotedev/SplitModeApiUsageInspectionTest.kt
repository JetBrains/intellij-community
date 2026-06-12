// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections.remotedev

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeApiRestrictionsService
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeApiUsageInspection
import kotlin.time.Duration.Companion.seconds

class SplitModeApiUsageInspectionTest : LightJavaCodeInsightFixtureTestCase() {

  override fun getBasePath(): String = "inspections/apiUsageRestrictedToModuleType"

  override fun setUp() {
    super.setUp()
    IntelliJProjectUtil.markAsIntelliJPlatformProject(project, true)

    val service = SplitModeApiRestrictionsService.getInstance(project)
    service.scheduleLoadRestrictions()
    timeoutRunBlocking {
      waitUntil("API restrictions failed to load", 2.seconds) { service.isLoaded() }
    }

    PsiTestUtil.addResourceContentToRoots(module, myFixture.tempDirFixture.findOrCreateDir("resources"), false)

    myFixture.addClass("""
      package com.intellij.util.remdev;

      import org.jetbrains.annotations.ApiStatus;

      import java.lang.annotation.ElementType;
      import java.lang.annotation.Retention;
      import java.lang.annotation.RetentionPolicy;
      import java.lang.annotation.Target;

      /**
       * Marks an API that can only be used from frontend modules in split mode.
       */
      @Retention(RetentionPolicy.CLASS)
      @Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})
      @ApiStatus.Internal
      public @interface FrontendApi {
      }
    """.trimIndent())

    myFixture.addClass("""
      package com.intellij.util.remdev;

      import org.jetbrains.annotations.ApiStatus;

      import java.lang.annotation.Documented;
      import java.lang.annotation.ElementType;
      import java.lang.annotation.Retention;
      import java.lang.annotation.RetentionPolicy;
      import java.lang.annotation.Target;

      /**
       * Marks an API that can only be used from backend modules in split mode.
       */
      @Retention(RetentionPolicy.CLASS)
      @Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})
      @ApiStatus.Internal
      public @interface BackendApi {
      }

    """.trimIndent())
    // Frontend API: com.intellij.openapi.wm.ToolWindowFactory
    myFixture.addClass(
      """
      package com.intellij.openapi.wm;
            
      public interface ToolWindowFactory {}
    """.trimIndent()
    )

    myFixture.addClass(
      """
      package com.intellij.openapi.fileEditor;
            
      public interface FileEditorManager {
        static FileEditorManager getInstance() {
          return null;
        }
        
        void getFocusedEditor();
      }
    """.trimIndent()
    )

    myFixture.addClass(
      """
      package com.intellij.openapi.wm;
      
      public interface ToolWindow {}
    """.trimIndent()
    )

    myFixture.addClass(
      """
      package com.intellij.openapi.project;
      
      public interface Project {}
    """.trimIndent()
    )

    myFixture.addClass(
      """
      package com.intellij.openapi.options;

      public interface Configurable {}
    """.trimIndent()
    )

    myFixture.addClass(
      """
      package com.intellij.openapi.fileEditor;

      public interface FileEditorManagerListener {}
    """.trimIndent()
    )

    myFixture.addClass(
      """
      package com.intellij.ide.plugins;

      public interface DynamicPluginListener {}
    """.trimIndent()
    )

    myFixture.addClass(
      """
      package com.intellij.lang;

      public interface ParserDefinition {}
    """.trimIndent()
    )

    myFixture.addClass(
      """
      package com.intellij.openapi.project;

      public interface ProjectManagerListener {}
    """.trimIndent()
    )

    // Backend API: com.intellij.openapi.vfs.VirtualFileManager
    myFixture.addClass(
      """
      package com.intellij.openapi.vfs;
      
      public abstract class VirtualFileManager {
        public static VirtualFileManager getInstance() {
          return null;
        }
        
        public abstract VirtualFile findFileByUrl(String url);
      }
    """.trimIndent()
    )

    myFixture.addClass(
      """
      package com.intellij.openapi.vfs;
      
      public interface VirtualFile {}
    """.trimIndent()
    )

    myFixture.addClass(
      """
      package com.example.annotated;

      import com.intellij.util.remdev.BackendApi;

      @BackendApi
      public final class AnnotatedBackendApi {
        public static AnnotatedBackendApi getInstance() {
          return null;
        }
      }
    """.trimIndent()
    )

    myFixture.addClass(
      """
      package com.example.annotated;

      import com.intellij.util.remdev.FrontendApi;

      @FrontendApi
      public final class AnnotatedFrontendApi {
        public static AnnotatedFrontendApi getInstance() {
          return null;
        }
      }
    """.trimIndent()
    )

    myFixture.enableInspections(SplitModeApiUsageInspection())
  }

  private fun configurePluginXml(pluginXmlContent: String) {
    myFixture.addFileToProject("resources/META-INF/plugin.xml", pluginXmlContent)
  }

  private fun configureContentModuleXml(pluginXmlContent: String) {
    myFixture.addFileToProject("resources/light_idea_test_case.xml", pluginXmlContent)
  }

  fun testFrontendApiInBackendModule() {
    configurePluginXml(
      """
      <idea-plugin>
        <dependencies>
          <module name="intellij.platform.backend"/>
        </dependencies>
      </idea-plugin>
    """.trimIndent()
    )

    myFixture.configureByText(
      "BackendService.kt", """
      package com.example.backend
      
      import com.intellij.openapi.wm.ToolWindowFactory
      import com.intellij.openapi.wm.ToolWindow
      import com.intellij.openapi.project.Project
      import com.intellij.openapi.vfs.VirtualFileManager
      import com.intellij.openapi.fileEditor.FileEditorManager;
      
      class CustomToolWindowFactory: <weak_warning descr="'com.intellij.openapi.wm.ToolWindowFactory' should be used in 'frontend' module type. Actual module type is 'backend'.

Computed module kind reasoning:

Backend dependency 'intellij.platform.backend' from descriptor 'plugin.xml' in module 'light_idea_test_case'">ToolWindowFactory</weak_warning> {}
      
      class BackendService {
        fun doStuff() {
          // no warning here expected
          VirtualFileManager.getInstance()
          
          <weak_warning descr="'com.intellij.openapi.fileEditor.FileEditorManager.getFocusedEditor' should be used in 'frontend' module type. Actual module type is 'backend'.

Computed module kind reasoning:

Backend dependency 'intellij.platform.backend' from descriptor 'plugin.xml' in module 'light_idea_test_case'">FileEditorManager.getInstance().getFocusedEditor()</weak_warning>
        }
      }
    """.trimIndent()
    )
    myFixture.checkHighlighting()
  }

  fun testCodeInspectionIsSkippedForPluginIdWithPredefinedModuleKind() {
    configurePluginXml(
      """
      <idea-plugin>
        <id>com.intellij.modules.lang</id>
      </idea-plugin>
    """.trimIndent()
    )

    myFixture.configureByText(
      "SharedLangModule.kt", """
      package com.example.shared

      import com.intellij.openapi.vfs.VirtualFileManager

      class SharedLangModule {
        fun test() {
          VirtualFileManager.getInstance()
        }
      }
    """.trimIndent()
    )

    myFixture.checkHighlighting()
  }

  fun testCodeInspectionIsNotSkippedForPluginIdWithPredefinedModuleKindWhenFlagDisabled() {
    RegistryManager.getInstance().get("devkit.split.mode.inspections.skip.predefined")
      .setValue(false, testRootDisposable)

    configurePluginXml(
      """
      <idea-plugin>
        <id>com.intellij.modules.lang</id>
      </idea-plugin>
    """.trimIndent()
    )

    myFixture.configureByText(
      "SharedLangModule.kt", """
      package com.example.shared

      import com.intellij.openapi.vfs.VirtualFileManager

      class SharedLangModule {
        fun test() {
          <weak_warning descr="'com.intellij.openapi.vfs.VirtualFileManager' should be used in 'backend' module type. Actual module type is 'shared'.

Computed module kind reasoning:

Predefined module kind for plugin/module id 'com.intellij.modules.lang'">VirtualFileManager</weak_warning>.getInstance()
        }
      }
    """.trimIndent()
    )

    myFixture.checkHighlighting()
  }

  fun testBackendApiInFrontendModule() {
    configurePluginXml(
      """
      <idea-plugin>
        <dependencies>
          <module name="intellij.platform.frontend"/>
        </dependencies>
      </idea-plugin>
    """.trimIndent()
    )

    myFixture.configureByText(
      "FrontendService.kt", """
      package com.example.frontend
      
      import com.intellij.openapi.wm.ToolWindowFactory
      import com.intellij.openapi.wm.ToolWindow
      import com.intellij.openapi.project.Project
      import com.intellij.openapi.vfs.VirtualFileManager
      
      // no warning here expected
      class CustomToolWindowFactory: ToolWindowFactory {}

      class FrontendService {
        fun doStuff() {
          <weak_warning descr="'com.intellij.openapi.vfs.VirtualFileManager' should be used in 'backend' module type. Actual module type is 'frontend'.

Computed module kind reasoning:

Frontend dependency 'intellij.platform.frontend' from descriptor 'plugin.xml' in module 'light_idea_test_case'">VirtualFileManager</weak_warning>.getInstance()
        }
      }
    """.trimIndent()
    )

    myFixture.checkHighlighting()
  }

  fun testConfigurableApiInBackendModule() {
    configurePluginXml(
      """
      <idea-plugin>
        <dependencies>
          <module name="intellij.platform.backend"/>
        </dependencies>
      </idea-plugin>
    """.trimIndent()
    )

    myFixture.configureByText(
      "BackendConfigurable.kt", """
      package com.example.backend

      import com.intellij.openapi.options.Configurable

      class BackendConfigurable : Configurable
    """.trimIndent()
    )

    myFixture.checkHighlighting()
  }

  fun testFrontendApiInBackendModuleForFileEditorManagerListener() {
    configurePluginXml(
      """
      <idea-plugin>
        <dependencies>
          <module name="intellij.platform.backend"/>
        </dependencies>
      </idea-plugin>
    """.trimIndent()
    )

    myFixture.configureByText(
      "BackendFileEditorListener.kt", """
      package com.example.backend

      import com.intellij.openapi.fileEditor.FileEditorManagerListener

      class BackendFileEditorListener : <weak_warning descr="'com.intellij.openapi.fileEditor.FileEditorManagerListener' should be used in 'frontend' module type. Actual module type is 'backend'.

Computed module kind reasoning:

Backend dependency 'intellij.platform.backend' from descriptor 'plugin.xml' in module 'light_idea_test_case'">FileEditorManagerListener</weak_warning>
    """.trimIndent()
    )

    myFixture.checkHighlighting()
  }

  fun testSharedApiInFrontendModuleForParserDefinition() {
    configurePluginXml(
      """
      <idea-plugin>
        <dependencies>
          <module name="intellij.platform.frontend"/>
        </dependencies>
      </idea-plugin>
    """.trimIndent()
    )

    myFixture.configureByText(
      "FrontendParserDefinition.kt", """
      package com.example.frontend

      import com.intellij.lang.ParserDefinition

      class FrontendParserDefinition : <weak_warning descr="'com.intellij.lang.ParserDefinition' should be used in 'shared' module type. Actual module type is 'frontend'.

Computed module kind reasoning:

Frontend dependency 'intellij.platform.frontend' from descriptor 'plugin.xml' in module 'light_idea_test_case'">ParserDefinition</weak_warning>
    """.trimIndent()
    )

    myFixture.checkHighlighting()
  }

  fun testBackendLifecycleListenerInFrontendModule() {
    configurePluginXml(
      """
      <idea-plugin>
        <dependencies>
          <module name="intellij.platform.frontend"/>
        </dependencies>
      </idea-plugin>
    """.trimIndent()
    )

    myFixture.configureByText(
      "FrontendProjectManagerListener.kt", """
      package com.example.frontend

      import com.intellij.openapi.project.ProjectManagerListener

      class FrontendProjectManagerListener : <weak_warning descr="'com.intellij.openapi.project.ProjectManagerListener' should be used in 'backend' module type. Actual module type is 'frontend'.

Computed module kind reasoning:

Frontend dependency 'intellij.platform.frontend' from descriptor 'plugin.xml' in module 'light_idea_test_case'">ProjectManagerListener</weak_warning>
    """.trimIndent()
    )

    myFixture.checkHighlighting()
  }

  fun testBackendApiInFrontendContentModule() {
    configureContentModuleXml(
      """
      <idea-plugin>
        <dependencies>
          <module name="intellij.platform.frontend"/>
        </dependencies>
      </idea-plugin>
    """.trimIndent()
    )

    myFixture.configureByText(
      "FrontendService.kt", """
      package com.example.frontend
      
      import com.intellij.openapi.wm.ToolWindowFactory
      import com.intellij.openapi.wm.ToolWindow
      import com.intellij.openapi.project.Project
      import com.intellij.openapi.vfs.VirtualFileManager
      
      // no warning here expected
      class CustomToolWindowFactory: ToolWindowFactory {}

      class FrontendService {
        fun doStuff() {
          <weak_warning descr="'com.intellij.openapi.vfs.VirtualFileManager' should be used in 'backend' module type. Actual module type is 'frontend'.

Computed module kind reasoning:

Frontend dependency 'intellij.platform.frontend' from descriptor 'light_idea_test_case.xml' in module 'light_idea_test_case'">VirtualFileManager</weak_warning>.getInstance()
        }
      }
    """.trimIndent()
    )

    myFixture.checkHighlighting()
  }

  fun testAnnotatedBackendApiInFrontendModule() {
    configurePluginXml(
      """
      <idea-plugin>
        <dependencies>
          <module name="intellij.platform.frontend"/>
        </dependencies>
      </idea-plugin>
    """.trimIndent()
    )

    myFixture.configureByText(
      "FrontendService.kt", """
      package com.example.frontend
      
      import com.example.annotated.AnnotatedBackendApi
      
      class FrontendService {
        fun doStuff() {
          <weak_warning descr="'com.example.annotated.AnnotatedBackendApi' should be used in 'backend' module type. Actual module type is 'frontend'.

Computed module kind reasoning:

Frontend dependency 'intellij.platform.frontend' from descriptor 'plugin.xml' in module 'light_idea_test_case'">AnnotatedBackendApi</weak_warning>.getInstance()
        }
      }
    """.trimIndent()
    )

    myFixture.checkHighlighting()
  }

  fun testAnnotatedFrontendApiInBackendModule() {
    configurePluginXml(
      """
      <idea-plugin>
        <dependencies>
          <module name="intellij.platform.backend"/>
        </dependencies>
      </idea-plugin>
    """.trimIndent()
    )

    myFixture.configureByText(
      "BackendService.kt", """
      package com.example.backend
      
      import com.example.annotated.AnnotatedFrontendApi
      
      class BackendService {
        fun doStuff() {
          <weak_warning descr="'com.example.annotated.AnnotatedFrontendApi' should be used in 'frontend' module type. Actual module type is 'backend'.

Computed module kind reasoning:

Backend dependency 'intellij.platform.backend' from descriptor 'plugin.xml' in module 'light_idea_test_case'">AnnotatedFrontendApi</weak_warning>.getInstance()
        }
      }
    """.trimIndent()
    )

    myFixture.checkHighlighting()
  }


  fun testWarningsInSharedModule() {
    configurePluginXml(
      """
      <idea-plugin>
        <dependencies>
          <module name="intellij.platform.core"/>
        </dependencies>
      </idea-plugin>
    """.trimIndent()
    )

    myFixture.configureByText(
      "SharedService.kt", """
      package com.example.shared
      
      import com.intellij.openapi.wm.ToolWindowFactory
      import com.intellij.openapi.vfs.VirtualFileManager
      
      // both warnings are expected in a shared module
      class SharedService {
        fun testFrontendApi() {
          class MyToolWindow: <weak_warning descr="'com.intellij.openapi.wm.ToolWindowFactory' should be used in 'frontend' module type. Actual module type is 'shared'.

Computed module kind reasoning:

No frontend or backend dependencies were found for module 'light_idea_test_case'">ToolWindowFactory</weak_warning> {}
        }
        
        fun testBackendApi() {
          <weak_warning descr="'com.intellij.openapi.vfs.VirtualFileManager' should be used in 'backend' module type. Actual module type is 'shared'.

Computed module kind reasoning:

No frontend or backend dependencies were found for module 'light_idea_test_case'">VirtualFileManager</weak_warning>.getInstance()
        }
      }
    """.trimIndent()
    )

    myFixture.checkHighlighting()
  }

  fun testFrontendOrBackendApiInSharedModule() {
    configurePluginXml(
      """
      <idea-plugin>
        <dependencies>
          <module name="intellij.platform.core"/>
        </dependencies>
      </idea-plugin>
    """.trimIndent()
    )

    myFixture.configureByText(
      "SharedDynamicPluginListener.kt", """
      package com.example.shared

      import com.intellij.ide.plugins.DynamicPluginListener

      class SharedDynamicPluginListener : <weak_warning descr="'com.intellij.ide.plugins.DynamicPluginListener' should be used in 'frontend or backend' module type. Actual module type is 'shared'.

Plugin lists are different on frontend and backend, prefer listening to them explicitly in desired IDE part

Computed module kind reasoning:

No frontend or backend dependencies were found for module 'light_idea_test_case'">DynamicPluginListener</weak_warning>
    """.trimIndent()
    )

    myFixture.checkHighlighting()
  }

  fun testNoWarningsInMixedModule() {
    configurePluginXml(
      """
      <idea-plugin>
        <dependencies>
          <module name="intellij.platform.frontend"/>
          <module name="intellij.platform.backend"/>
        </dependencies>
      </idea-plugin>
    """.trimIndent()
    )

    myFixture.configureByText(
      "MixedService.kt", """
      package com.example.mixed

      import com.intellij.openapi.wm.ToolWindowFactory
      import com.intellij.openapi.vfs.VirtualFileManager

      class MixedService {
        fun testFrontendApi() {
          class MyToolWindow: ToolWindowFactory {}
        }

        fun testBackendApi() {
          VirtualFileManager.getInstance()
        }
      }
    """.trimIndent()
    )

    myFixture.checkHighlighting()
  }

  fun testNoWarningsInMonolithModule() {
    configurePluginXml(
      """
      <idea-plugin>
        <dependencies>
          <module name="intellij.platform.monolith"/>
        </dependencies>
      </idea-plugin>
    """.trimIndent()
    )

    myFixture.configureByText(
      "MonolithService.kt", """
      package com.example.monolith

      import com.intellij.openapi.wm.ToolWindowFactory
      import com.intellij.openapi.vfs.VirtualFileManager

      class MonolithService {
        fun testFrontendApi() {
          class MyToolWindow: ToolWindowFactory {}
        }

        fun testBackendApi() {
          VirtualFileManager.getInstance()
        }
      }
    """.trimIndent()
    )

    myFixture.checkHighlighting()
  }

  fun testAddFrontendDependencyFix() {
    configurePluginXml(
      """
      <idea-plugin>
        <dependencies>
          <module name="intellij.platform.core"/>
        </dependencies>
      </idea-plugin>
    """.trimIndent()
    )

    myFixture.configureByText(
      "SharedFrontendService.kt", """
      package com.example.shared

      import com.intellij.openapi.wm.ToolWindowFactory

      class SharedFrontendService {
        fun testFrontendApi() {
          class MyToolWindow: <caret>ToolWindowFactory {}
        }
      }
    """.trimIndent()
    )

    launchActionAndWait("Make module 'light_idea_test_case' work in 'frontend' only") {
      getModuleDependencyNames().contains("intellij.platform.frontend")
    }

    val pluginXml = myFixture.findFileInTempDir("resources/META-INF/plugin.xml")
    val result = FileDocumentManager.getInstance().getDocument(pluginXml)!!.text
    assertTrue(result.contains("<module name=\"intellij.platform.core\"/>"))
    assertTrue(result.contains("<module name=\"intellij.platform.frontend\"/>"))
    assertTrue(getModuleDependencyNames().contains("intellij.platform.frontend"))
  }

  fun testMakeModuleMonolithOnlyFixForFrontendApiInBackendModule() {
    configurePluginXml(
      """
      <idea-plugin>
        <dependencies>
          <module name="intellij.platform.core"/>
          <module name="intellij.platform.backend"/>
          <plugin id="com.jetbrains.remoteDevelopment"/>
        </dependencies>
      </idea-plugin>
    """.trimIndent()
    )

    myFixture.configureByText(
      "BackendService.kt", """
      package com.example.backend

      import com.intellij.openapi.wm.ToolWindowFactory

      class BackendService {
        fun testFrontendApi() {
          class MyToolWindow: <caret>ToolWindowFactory {}
        }
      }
    """.trimIndent()
    )
    addCurrentModuleDependencies("intellij.platform.backend", "com.jetbrains.remoteDevelopment")

    val intention = myFixture.findSingleIntention("Make module 'light_idea_test_case' work in 'monolith' only")
    myFixture.launchAction(intention)

    val pluginXml = myFixture.findFileInTempDir("resources/META-INF/plugin.xml")
    val result = FileDocumentManager.getInstance().getDocument(pluginXml)!!.text
    assertTrue(result.contains("<module name=\"intellij.platform.core\"/>"))
    assertTrue(result.contains("<module name=\"intellij.platform.monolith\"/>"))
    assertFalse(result.contains("intellij.platform.backend"))
    assertTrue(result.contains("<plugin id=\"com.jetbrains.remoteDevelopment\"/>"))
    assertTrue(getModuleDependencyNames().contains("intellij.platform.monolith"))
    assertFalse(getModuleDependencyNames().contains("intellij.platform.backend"))
    myFixture.checkHighlighting()
  }

  fun testMakeModuleMonolithOnlyFixForBackendApiInFrontendModule() {
    configurePluginXml(
      """
      <idea-plugin>
        <dependencies>
          <module name="intellij.platform.core"/>
          <module name="intellij.platform.frontend"/>
          <plugin id="com.intellij.jetbrains.client"/>
        </dependencies>
      </idea-plugin>
    """.trimIndent()
    )

    myFixture.configureByText(
      "FrontendService.kt", """
      package com.example.frontend

      import com.intellij.openapi.vfs.VirtualFileManager

      class FrontendService {
        fun testBackendApi() {
          <caret>VirtualFileManager.getInstance()
        }
      }
    """.trimIndent()
    )
    addCurrentModuleDependencies("intellij.platform.frontend", "com.intellij.jetbrains.client")

    val intention = myFixture.findSingleIntention("Make module 'light_idea_test_case' work in 'monolith' only")
    myFixture.launchAction(intention)

    val pluginXml = myFixture.findFileInTempDir("resources/META-INF/plugin.xml")
    val result = FileDocumentManager.getInstance().getDocument(pluginXml)!!.text
    assertTrue(result.contains("<module name=\"intellij.platform.core\"/>"))
    assertTrue(result.contains("<module name=\"intellij.platform.monolith\"/>"))
    assertFalse(result.contains("intellij.platform.frontend"))
    assertTrue(result.contains("<plugin id=\"com.intellij.jetbrains.client\"/>"))
    assertTrue(getModuleDependencyNames().contains("intellij.platform.monolith"))
    assertFalse(getModuleDependencyNames().contains("intellij.platform.frontend"))
    myFixture.checkHighlighting()
  }

  fun testMakeModuleHaveOnlyBackendDependenciesFix() {
    configurePluginXml(
      """
      <idea-plugin>
        <dependencies>
          <module name="intellij.platform.core"/>
          <module name="intellij.platform.frontend"/>
        </dependencies>
      </idea-plugin>
    """.trimIndent()
    )

    myFixture.configureByText(
      "FrontendService.kt", """
      package com.example.frontend

      import com.intellij.openapi.vfs.VirtualFileManager

      class FrontendService {
        fun doStuff() {
          <caret>VirtualFileManager.getInstance()
        }
      }
    """.trimIndent()
    )
    addCurrentModuleDependencies("intellij.platform.frontend")

    launchActionAndWait("Make module 'light_idea_test_case' work in 'backend' only") {
      !getModuleDependencyNames().contains("intellij.platform.frontend")
    }

    val pluginXml = myFixture.findFileInTempDir("resources/META-INF/plugin.xml")
    val result = FileDocumentManager.getInstance().getDocument(pluginXml)!!.text
    assertTrue(result.contains("<module name=\"intellij.platform.core\"/>"))
    assertFalse(result.contains("intellij.platform.frontend"))
    assertFalse(getModuleDependencyNames().contains("intellij.platform.frontend"))
  }

  private fun getModuleDependencyNames(): Set<String> {
    return ModuleRootManager.getInstance(module).orderEntries.filterIsInstance<ModuleOrderEntry>().map { it.moduleName }.toSet()
  }

  private fun addCurrentModuleDependencies(vararg dependencyNames: String) {
    ModuleRootModificationUtil.updateModel(module) { model ->
      for (dependencyName in dependencyNames) {
        if (model.orderEntries.filterIsInstance<ModuleOrderEntry>().none { it.moduleName == dependencyName }) {
          model.addInvalidModuleEntry(dependencyName)
        }
      }
    }
  }

  private fun launchActionAndWait(intentionText: String, condition: () -> Boolean) {
    val intention = myFixture.findSingleIntention(intentionText)
    launchActionAndWait(intention, condition)
  }

  private fun launchActionAndWait(intention: IntentionAction, condition: () -> Boolean) {
    myFixture.launchAction(intention)
    timeoutRunBlocking {
      waitUntil("Quick fix was not applied", 5.seconds) { condition() }
    }
  }
}
