// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.action;

import com.intellij.codeInsight.editorActions.CopyPastePreProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RawText;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.JavaXmlDocumentKt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.statistics.GradleActionsUsagesCollector;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import java.io.IOException;
import java.io.StringReader;

public final class PasteMvnDependencyPreProcessor implements CopyPastePreProcessor {

  @Nullable
  @Override
  public String preprocessOnCopy(PsiFile file, int[] startOffsets, int[] endOffsets, String text) {
    return null;
  }

  @NotNull
  @Override
  public String preprocessOnPaste(Project project, PsiFile file, Editor editor, String text, RawText rawText) {
    if (isApplicable(file) && isMvnDependency(text)) {
      GradleActionsUsagesCollector.trigger(project, GradleActionsUsagesCollector.PASTE_MAVEN_DEPENDENCY);
      boolean isKotlinDsl = isKotlinBuildScriptFile(file.getName());
      return toGradleDependency(text, isKotlinDsl);
    }
    return text;
  }

  private boolean isApplicable(PsiFile file) {
    return file.getName().endsWith('.' + GradleConstants.EXTENSION) || isKotlinBuildScriptFile(file.getName());
  }

  private static boolean isKotlinBuildScriptFile(String filename) {
    return filename.endsWith('.' + GradleConstants.KOTLIN_DSL_SCRIPT_EXTENSION);
  }

  private static @NotNull String formatGradleDependency(@NotNull String groupId,
                                                        @NotNull String artifactId,
                                                        @NotNull String version,
                                                        @NotNull String scope,
                                                        @NotNull String classifier,
                                                        boolean isKotlinDsl) {
    String gradleClassifier = classifier.isEmpty() ? "" : ":" + classifier;
    String gradleVersion = version.isEmpty() ? "" : ":" + version;
    StringBuilder dependency = new StringBuilder()
      .append(scope)
      .append(isKotlinDsl ? "(\"" : " '")
      .append(groupId).append(':').append(artifactId)
      .append(gradleVersion)
      .append(gradleClassifier)
      .append(isKotlinDsl ? "\")" : "'");

    return dependency.toString();
  }

  @ApiStatus.Internal
  public static @NotNull String toGradleDependency(@NotNull String mavenDependency, boolean isKotlinDsl) {
    try {
      DocumentBuilder builder = JavaXmlDocumentKt.createDocumentBuilder();
      Document document = builder.parse(new InputSource(new StringReader(mavenDependency)));
      String gradleDependency = extractGradleDependency(document, isKotlinDsl);
      return gradleDependency != null ? gradleDependency : mavenDependency;
    }
    catch (SAXException | IOException ignored) {
    }

    return mavenDependency;
  }

  @Nullable
  private static String extractGradleDependency(Document document, boolean isKotlinDsl) {
    String groupId = getGroupId(document);
    String artifactId = getArtifactId(document);
    String version = getVersion(document);
    String scope = getScope(document);
    String classifier = getClassifier(document);

    if (groupId.isEmpty() || artifactId.isEmpty()) {
      return null;
    }
    return formatGradleDependency(groupId, artifactId, version, scope, classifier, isKotlinDsl);
  }

  @NotNull
  private static String getScope(@NotNull Document document) {
    String scope = firstOrEmpty(document.getElementsByTagName("scope"));
    return switch (scope) {
      case "test" -> "testImplementation";
      case "provided" -> "compileOnly";
      case "runtime" -> "runtime";
      case "compile" -> "implementation";
      default -> "implementation";
    };
  }

  private static String getVersion(@NotNull Document document) {
    return firstOrEmpty(document.getElementsByTagName("version"));
  }

  private static String getArtifactId(@NotNull Document document) {
    return firstOrEmpty(document.getElementsByTagName("artifactId"));
  }

  private static String getGroupId(@NotNull Document document) {
    return firstOrEmpty(document.getElementsByTagName("groupId"));
  }

  private static String getClassifier(@NotNull Document document) {
    return firstOrEmpty(document.getElementsByTagName("classifier"));
  }

  private static String firstOrEmpty(@NotNull NodeList list) {
    Node first = list.item(0);
    return first != null ? first.getTextContent() : "";
  }

  private static boolean isMvnDependency(String text) {
    String trimmed = trimLeadingComment(text.trim());
    if (trimmed.startsWith("<dependency>") && trimmed.endsWith("</dependency>")) {
      return true;
    }
    return false;
  }

  /**
   * Removes leading comment, usually it exists if dependency was copied from maven central site
   */
  private static String trimLeadingComment(String text) {
    int start = text.indexOf("<!--");
    int end = text.indexOf("-->");
    if (start == 0 && end > 0) {
      return text.substring(end + "-->".length()).trim();
    }
    else {
      return text;
    }
  }

  @Override
  public boolean requiresAllDocumentsToBeCommitted(@NotNull Editor editor, @NotNull Project project) {
    return false;
  }
}
