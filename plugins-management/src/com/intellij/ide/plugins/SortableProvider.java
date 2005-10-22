package com.intellij.ide.plugins;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Dec 26, 2003
 * Time: 4:31:57 PM
 * To change this template use Options | File Templates.
 */
public interface SortableProvider {
  int getSortOrder ();
  void setSortOrder (int sortOrder);
  int getSortColumn ();
  void setSortColumn (int sortColumn);
}
