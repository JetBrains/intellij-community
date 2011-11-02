package org.jetbrains.plugins.groovy.util;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.util.containers.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;

/**
 * @author Sergey Evdokimov
 */
public class ClassInstanceCache {

  private static final ConcurrentHashMap<String, Object> CACHE = new ConcurrentHashMap<String, Object>();

  private ClassInstanceCache() {
  }

  private static Object createInstance(@NotNull String className) {
    try {
      try {
        return Class.forName(className).newInstance();
      }
      catch (ClassNotFoundException e) {
        for (IdeaPluginDescriptor descriptor : PluginManager.getPlugins()) {
          try {
            return descriptor.getPluginClassLoader().loadClass(className).newInstance();
          }
          catch (ClassNotFoundException ignored) {

          }
        }

        throw new RuntimeException("Class not found: " + className);
      }
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings("unchecked")
  public static <T> T getInstance(@NotNull String className, ClassLoader classLoader) {
    Object res = CACHE.get(className);
    if (res == null) {
      res = createInstance(className);

      Object oldValue = CACHE.putIfAbsent(className, res);
      if (oldValue != null) {
        res = oldValue;
      }
    }

    return (T)res;
  }
  
}
