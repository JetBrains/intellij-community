package org.jetbrains.android.dom.converters;

/**
 * @author Eugene.Kudelevsky
 */
public class FragmentClassConverter extends PackageClassConverter {
  public FragmentClassConverter() {
    super("android.app.Fragment", "android.support.v4.app.Fragment");
  }
}
