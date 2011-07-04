package org.jetbrains.android.importDependencies;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
* @author Eugene.Kudelevsky
*/
abstract class ImportDependenciesTask {

  @Nullable
  public abstract Exception perform();

  @NotNull
  public abstract String getTitle();
}
