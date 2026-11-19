// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.idea.devkit.inspections.PluginModuleFixture
import org.jetbrains.idea.devkit.inspections.pluginModuleFixture

/**
 * Creates a [PluginModuleFixture] of the Kotlin object extension registration tests for one test. Declare it as an instance field.
 * Give it the project of [org.jetbrains.idea.devkit.inspections.pluginModuleProjectFixture].
 *
 * [basePath] is the test data path under the community directory. The fixture enables the inspection that [inspection] creates.
 * The project has the stubs of the extension API, and `extensionPointsDeclarations.xml` of the test data is the plugin descriptor.
 */
internal fun kotlinObjectExtensionRegistrationFixture(
  projectFixture: TestFixture<LightJavaProject>,
  basePath: String,
  inspection: () -> LocalInspectionTool,
): TestFixture<PluginModuleFixture> = testFixture("kotlinObjectExtensionRegistrationFixture") {
  val pluginModule = pluginModuleFixture(projectFixture, basePath).init()
  val fixture = pluginModule.fixture
  fixture.addClass("""
    package com.intellij.util.xmlb.annotations;
    import java.lang.annotation.*;
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.TYPE, ElementType.METHOD})
    public @interface Tag {
      String value() default "";
    }
  """.trimIndent())
  fixture.addClass("""
    package com.intellij.util.xmlb.annotations;
    import java.lang.annotation.*;
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.TYPE, ElementType.METHOD})
    public @interface Attribute {
      String value() default "";
    }
  """.trimIndent())
  fixture.addClass("""
    package com.intellij.openapi.options;
    import com.intellij.util.xmlb.annotations.*;
    @Tag("configurable")
    public class ConfigurableEP {
      @Attribute("instance") public String instanceClass;
      @Attribute("implementation") public String implementationClass;
      @Attribute("provider") public String providerClass;
    }
  """.trimIndent())
  fixture.addClass("""
    package com.intellij.openapi.options;
    public interface Configurable {
      JComponent createComponent();
      boolean isModified();
      void apply();
      String getDisplayName();
    }
  """.trimIndent())
  fixture.addClass("""
    package com.intellij.openapi.options;
    public abstract class ConfigurableProvider {
      public abstract Configurable createConfigurable();
    }
  """.trimIndent())
  fixture.addClass("""
    package com.intellij.openapi.fileTypes.impl;
    import com.intellij.util.xmlb.annotations.*;
    public final class FileTypeBean {
      @Attribute("implementationClass") public String implementationClass;
      @Attribute("fieldName") public String fieldName;
      @Attribute("name") public String name;
    }
  """.trimIndent())
  fixture.addClass("""
    package com.intellij.openapi.fileTypes;
    import javax.swing.Icon;
    public interface FileType {
      String getName();
      String getDescription();
      String getDefaultExtension();
      Icon getIcon();
      boolean isBinary();
    }
  """.trimIndent())
  fixture.addClass("""
    package com.intellij.lang;
    public abstract class Language {
      public static final Language ANY = null;
    }
  """.trimIndent())
  fixture.addClass("""
    package com.intellij.openapi.fileTypes;
    import com.intellij.lang.Language;
    public abstract class LanguageFileType implements FileType {
      protected LanguageFileType(Language language);
      @Override public final boolean isBinary() { return false; }
    }
  """.trimIndent())
  fixture.addClass("""
    package com.intellij.openapi.project;
    public interface Project {}
  """.trimIndent())
  fixture.addClass("""
    package com.intellij.openapi.editor;
    public interface Editor {}
  """.trimIndent())
  fixture.addClass("""
    package com.intellij.psi;
    public interface PsiFile {}
  """.trimIndent())
  fixture.addClass("""
    package com.intellij.codeInsight.intention;
    import com.intellij.openapi.editor.Editor;
    import com.intellij.openapi.project.Project;
    import com.intellij.psi.PsiFile;
    public interface IntentionAction {
      String getText();
      String getFamilyName();
      boolean isAvailable(Project project, Editor editor, PsiFile file);
      void invoke(Project project, Editor editor, PsiFile file);
      @Override boolean startInWriteAction();
    }
  """.trimIndent())
  fixture.addClass("""
    package com.intellij.openapi.components;
    import com.intellij.util.xmlb.annotations.Attribute;
    public final class ServiceDescriptor {
      @Attribute public final String serviceImplementation;
      @Attribute public final String testServiceImplementation;
      @Attribute public final String headlessImplementation;
    }
  """.trimIndent())
  fixture.addClass("""
    package com.intellij.codeInsight.intention;
    import com.intellij.util.xmlb.annotations.Tag;
    public final class IntentionActionBean {
      @Tag public String className;
      @Tag public String language;
    }
  """.trimIndent())
  pluginModule.setPluginXml("extensionPointsDeclarations.xml")
  fixture.enableInspections(inspection())
  initialized(pluginModule) {}
}