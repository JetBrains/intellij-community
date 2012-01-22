package org.jetbrains.android.uipreview;

import com.android.ide.common.rendering.api.LayoutLog;
import com.android.ide.common.rendering.api.RenderResources;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class ResourceResolverDecorator extends RenderResources {
  private final ResourceResolver myWrappee;

  public ResourceResolverDecorator(@NotNull ResourceResolver wrappee) {
    myWrappee = wrappee;
  }

  @Override
  public void setFrameworkResourceIdProvider(FrameworkResourceIdProvider provider) {
    myWrappee.setFrameworkResourceIdProvider(provider);
  }

  @Override
  public void setLogger(LayoutLog logger) {
    myWrappee.setLogger(logger);
  }

  @Override
  public StyleResourceValue getCurrentTheme() {
    return myWrappee.getCurrentTheme();
  }

  @Override
  public StyleResourceValue getTheme(String name, boolean frameworkTheme) {
    return myWrappee.getTheme(name, frameworkTheme);
  }

  @Override
  public boolean themeIsParentOf(StyleResourceValue parentTheme, StyleResourceValue childTheme) {
    return myWrappee.themeIsParentOf(parentTheme, childTheme);
  }

  @Override
  public ResourceValue getFrameworkResource(ResourceType resourceType, String resourceName) {
    return myWrappee.getFrameworkResource(resourceType, resourceName);
  }

  @Override
  public ResourceValue getProjectResource(ResourceType resourceType, String resourceName) {
    return myWrappee.getProjectResource(resourceType, resourceName);
  }

  @Override
  public ResourceValue findItemInTheme(String itemName) {
    return myWrappee.findItemInTheme(itemName);
  }

  @Override
  public ResourceValue findItemInStyle(StyleResourceValue style, String itemName) {
    return myWrappee.findItemInStyle(style, itemName);
  }

  @Override
  public ResourceValue findResValue(String reference, boolean forceFrameworkOnly) {
    return myWrappee.findResValue(reference, forceFrameworkOnly);
  }

  @Nullable
  @Override
  public ResourceValue resolveValue(ResourceType type, String name, String value, boolean isFrameworkValue) {
    return myWrappee.resolveValue(type, name, value, isFrameworkValue);
  }

  @Nullable
  @Override
  public ResourceValue resolveResValue(ResourceValue value) {
    if (value == null) {
      return null;
    }
    final String stringValue = value.getValue();

    if (stringValue == null) {
      return value;
    }
    final ResourceValue resolvedResValue = myWrappee.findResValue(stringValue, value.isFramework());

    if (resolvedResValue == null) {
      return value;
    }

    // avoid infinite recursion (see http://code.google.com/p/android/issues/detail?id=24317)
    if (resolvedResValue.equals(value)) {
      // use default value
      return null;
    }
    return resolveResValue(resolvedResValue);
  }
}
