package org.jetbrains.plugins.gradle.config;

import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorAndFontDescriptorsProvider;
import com.intellij.openapi.options.colors.ColorDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleBundle;

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
    GradleBundle.message("gradle.sync.change.type.conflict"),
    GradleTextAttributes.CHANGE_CONFLICT
  );
  
  public static final AttributesDescriptor CONFIRMED = new AttributesDescriptor(
    GradleBundle.message("gradle.sync.change.type.confirmed"),
    GradleTextAttributes.CONFIRMED_CONFLICT
  );

  public static final AttributesDescriptor GRADLE_LOCAL = new AttributesDescriptor(
    GradleBundle.message("gradle.sync.change.type.gradle"),
    GradleTextAttributes.GRADLE_LOCAL_CHANGE
  );

  public static final AttributesDescriptor INTELLIJ_LOCAL = new AttributesDescriptor(
    GradleBundle.message("gradle.sync.change.type.intellij"),
    //GradleBundle.message("gradle.sync.change.type.intellij", ApplicationNamesInfo.getInstance().getProductName()),
    GradleTextAttributes.INTELLIJ_LOCAL_CHANGE
  );

  public static final AttributesDescriptor NO_CHANGE = new AttributesDescriptor(
    GradleBundle.message("gradle.sync.change.type.unchanged"),
    GradleTextAttributes.NO_CHANGE
  );
  
  public static final AttributesDescriptor[] DESCRIPTORS = { CONFLICT, /*CONFIRMED,*/ GRADLE_LOCAL, INTELLIJ_LOCAL, NO_CHANGE };

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
