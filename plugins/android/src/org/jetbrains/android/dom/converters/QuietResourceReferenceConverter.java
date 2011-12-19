package org.jetbrains.android.dom.converters;

/**
 * @author Eugene.Kudelevsky
 */
public class QuietResourceReferenceConverter extends ResourceReferenceConverter {
  public QuietResourceReferenceConverter() {
    setQuiet(true);
  }
}
