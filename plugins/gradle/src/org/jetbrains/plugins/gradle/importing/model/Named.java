package org.jetbrains.plugins.gradle.importing.model;

import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

/**
 * @author Denis Zhdanov
 * @since 8/12/11 12:34 PM
 */
public interface Named {

  Comparator<Named> COMPARATOR = new Comparator<Named>() {
    @Override
    public int compare(Named o1, Named o2) {
      return o1.getName().compareTo(o2.getName());
    }
  };
  
  @NotNull
  String getName();

  void setName(@NotNull String name);
}
