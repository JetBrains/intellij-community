package org.jetbrains.android.dom.converters;

/**
 * @author Eugene.Kudelevsky
 */
public class ParentStyleConverter extends ResourceReferenceConverter {
  public ParentStyleConverter() {
    super("style", false, false);
  }
}
