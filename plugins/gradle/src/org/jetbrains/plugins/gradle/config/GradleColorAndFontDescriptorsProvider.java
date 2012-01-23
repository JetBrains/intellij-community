package org.jetbrains.plugins.gradle.config;

import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorAndFontDescriptorsProvider;
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
public class GradleColorAndFontDescriptorsProvider implements ColorAndFontDescriptorsProvider {
  
  private static final AttributesDescriptor[] DESCRIPTORS = {
    new AttributesDescriptor(
      GradleBundle.message("gradle.sync.change.type.conflict"),
      GradleTextAttributes.GRADLE_CHANGE_CONFLICT
    ),
    new AttributesDescriptor(
      GradleBundle.message("gradle.sync.change.type.confirmed"),
      GradleTextAttributes.GRADLE_CONFIRMED_CONFLICT
    ),
    new AttributesDescriptor(
      GradleBundle.message("gradle.sync.change.type.gradle"),
      GradleTextAttributes.GRADLE_LOCAL_CHANGE
    ),
    new AttributesDescriptor(
      GradleBundle.message("gradle.sync.change.type.intellij", ApplicationNamesInfo.getInstance().getProductName()),
      GradleTextAttributes.GRADLE_INTELLIJ_LOCAL_CHANGE
    ),
    new AttributesDescriptor(
      GradleBundle.message("gradle.sync.change.type.unchanged"),
      GradleTextAttributes.GRADLE_NO_CHANGE
    )
  };

  @NotNull
  @Override
  public String getDisplayName() {
    return GradleBundle.message("gradle.name");
  }

  @NotNull
  @Override
  public AttributesDescriptor[] getAttributeDescriptors() {
    return DESCRIPTORS;
  }

  @NotNull
  @Override
  public ColorDescriptor[] getColorDescriptors() {
    return new ColorDescriptor[0];
  }
}
