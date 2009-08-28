package com.intellij.openapi.vcs.changes.committed;

public interface DecoratorManager {
  void install(final CommittedChangeListDecorator decorator);
  void remove(final CommittedChangeListDecorator decorator);
  void repaintTree();
  void reportLoadedLists(final CommittedChangeListsListener listener);
  void removeFilteringStrategy(final String key);
  boolean setFilteringStrategy(final String key, final ChangeListFilteringStrategy filteringStrategy);
}
