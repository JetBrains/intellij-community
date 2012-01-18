package org.jetbrains.plugins.gradle.config;

import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.ui.GradleIcons;
import org.jetbrains.plugins.gradle.util.GradleBundle;

import javax.swing.*;
import java.util.Map;

/**
 * Provides support for defining gradle-specific color settings.
 * <p/>
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 1/18/12 4:15 PM
 */
public class GradleColorSettingsPage implements ColorSettingsPage {
  
  private static final ColorDescriptor[] DESCRIPTORS = {
    new ColorDescriptor(
      GradleBundle.message("gradle.sync.change.type.gradle"),
      GradleColorKeys.GRADLE_LOCAL_CHANGE,
      ColorDescriptor.Kind.FOREGROUND
    ),
    new ColorDescriptor(
      GradleBundle.message("gradle.sync.change.type.intellij", ApplicationNamesInfo.getInstance().getProductName()),
      GradleColorKeys.GRADLE_INTELLIJ_LOCAL_CHANGE,
      ColorDescriptor.Kind.FOREGROUND
    ),
    new ColorDescriptor(
      GradleBundle.message("gradle.sync.change.type.conflict"),
      GradleColorKeys.GRADLE_CHANGE_CONFLICT,
      ColorDescriptor.Kind.FOREGROUND
    ),
    new ColorDescriptor(
      GradleBundle.message("gradle.sync.change.type.confirmed"),
      GradleColorKeys.GRADLE_CONFIRMED_CONFLICT,
      ColorDescriptor.Kind.FOREGROUND
    )
  };
  
  @NotNull
  @Override
  public String getDisplayName() {
    return GradleBundle.message("gradle.name");
  }

  @Override
  public Icon getIcon() {
    return GradleIcons.GRADLE_ICON;
  }

  @NotNull
  @Override
  public AttributesDescriptor[] getAttributeDescriptors() {
    return new AttributesDescriptor[0];
  }

  @NotNull
  @Override
  public ColorDescriptor[] getColorDescriptors() {
    return DESCRIPTORS;
  }

  @NotNull
  @Override
  public SyntaxHighlighter getHighlighter() {
    return new PlainSyntaxHighlighter();
  }

  @NotNull
  @Override
  public String getDemoText() {
    return "";
  }

  @Override
  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return null;
  }
}
