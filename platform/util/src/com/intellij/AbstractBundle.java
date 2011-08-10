package com.intellij;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.Reference;
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

  private Reference<ResourceBundle> myBundle;

  @NonNls
  private final String myPathToBundle;

  protected AbstractBundle(@NonNls @NotNull String pathToBundle) {
    myPathToBundle = pathToBundle;
  }

  public String getMessage(String key, Object... params) {
    return CommonBundle.message(getBundle(), key, params);
  }

  private ResourceBundle getBundle() {
    ResourceBundle bundle = null;
    if (myBundle != null) bundle = myBundle.get();
    if (bundle == null) {
      bundle = ResourceBundle.getBundle(myPathToBundle, Locale.getDefault(), getClass().getClassLoader());
      myBundle = new SoftReference<ResourceBundle>(bundle);
    }
    return bundle;
  }
}
