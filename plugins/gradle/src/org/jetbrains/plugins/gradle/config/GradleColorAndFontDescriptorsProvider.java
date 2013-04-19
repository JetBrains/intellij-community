package org.jetbrains.plugins.gradle.config;

import com.intellij.openapi.externalSystem.settings.ExternalSystemTextAttributes;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorAndFontDescriptorsProvider;
import com.intellij.openapi.options.colors.ColorDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * Provides support for defining gradle-specific color settings.
 * <p/>
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 1/18/12 4:15 PM
 */
public class GradleColorAndFontDescriptorsProvider implements ColorAndFontDescriptorsProvider {

  public static final AttributesDescriptor CONFLICT = new AttributesDescriptor(
    ExternalSystemBundle.message("gradle.sync.change.type.conflict"),
    ExternalSystemTextAttributes.CHANGE_CONFLICT
  );

  public static final AttributesDescriptor GRADLE_LOCAL = new AttributesDescriptor(
    ExternalSystemBundle.message("gradle.sync.change.type.gradle"),
    ExternalSystemTextAttributes.EXTERNAL_SYSTEM_LOCAL_CHANGE
  );

  public static final AttributesDescriptor INTELLIJ_LOCAL = new AttributesDescriptor(
    ExternalSystemBundle.message("gradle.sync.change.type.intellij"),
    //GradleBundle.message("gradle.sync.change.type.intellij", ApplicationNamesInfo.getInstance().getProductName()),
    ExternalSystemTextAttributes.IDE_LOCAL_CHANGE
  );

  public static final AttributesDescriptor OUTDATED_ENTITY = new AttributesDescriptor(
    ExternalSystemBundle.message("gradle.sync.change.type.changed.library.version"),
    //GradleBundle.message("gradle.sync.change.type.intellij", ApplicationNamesInfo.getInstance().getProductName()),
    ExternalSystemTextAttributes.OUTDATED_ENTITY
  );

  public static final AttributesDescriptor NO_CHANGE = new AttributesDescriptor(
    ExternalSystemBundle.message("gradle.sync.change.type.unchanged"),
    ExternalSystemTextAttributes.NO_CHANGE
  );

  public static final AttributesDescriptor[] DESCRIPTORS = {
    CONFLICT, GRADLE_LOCAL, INTELLIJ_LOCAL, OUTDATED_ENTITY, NO_CHANGE
  };

  @NotNull
  @Override
  public String getDisplayName() {
    return ExternalSystemBundle.message("gradle.name");
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
