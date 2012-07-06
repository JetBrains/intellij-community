package org.jetbrains.android.dom.resources;

import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public interface Plurals extends ResourceElement {
  List<PluralsItem> getItems();
}
