package com.intellij;

import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.containers.ConcurrentWeakFactoryMap;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.SoftReference;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Base class for particular scoped bundles (e.g. <code>'vcs'</code> bundles, <code>'aop'</code> bundles etc).
 * <p/>
 * Usage pattern:
 * <pre>
 * <ol>
 *   <li>Create class that extends this class and provides path to the target bundle to the current class constructor;</li>
 *   <li>
 *     Optionally create static facade method at the subclass - create single shared instance and delegate
 *     to its {@link #getMessage(String, Object...)};
 *   </li>
 * </ol>
 * </pre>
 *
 * @author Denis Zhdanov
 * @since 8/1/11 2:37 PM
 */
public class AbstractBundle {

  @NonNls
  private final String myPathToBundle;

  protected AbstractBundle(@NonNls @NotNull String pathToBundle) {
    myPathToBundle = pathToBundle;
  }

  public String getMessage(String key, Object... params) {
    return CommonBundle.message(getBundle(), key, params);
  }

  private ResourceBundle getBundle() {
    return getResourceBundle(myPathToBundle, getClass().getClassLoader());
  }
  
  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  private static FactoryMap<ClassLoader, ConcurrentHashMap<String, SoftReference<ResourceBundle>>> ourCache =
    new ConcurrentWeakFactoryMap<ClassLoader, ConcurrentHashMap<String, SoftReference<ResourceBundle>>>() {
    @Override
    protected ConcurrentHashMap<String, SoftReference<ResourceBundle>> create(ClassLoader key) {
      return new ConcurrentHashMap<String, SoftReference<ResourceBundle>>();
    }
  };

  public static ResourceBundle getResourceBundle(String pathToBundle, ClassLoader loader) {
    ConcurrentHashMap<String, SoftReference<ResourceBundle>> map = ourCache.get(loader);
    SoftReference<ResourceBundle> reference = map.get(pathToBundle);
    ResourceBundle result = reference == null ? null : reference.get();
    if (result == null) {
      map.put(pathToBundle, new SoftReference<ResourceBundle>(result = ResourceBundle.getBundle(pathToBundle, Locale.getDefault(), loader)));
    }
    return result;
  }
}
