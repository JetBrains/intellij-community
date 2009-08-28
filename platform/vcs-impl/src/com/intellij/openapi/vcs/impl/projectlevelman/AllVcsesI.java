package com.intellij.openapi.vcs.impl.projectlevelman;

import com.intellij.openapi.vcs.AbstractVcs;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 28.05.2009
 * Time: 13:30:26
 * To change this template use File | Settings | File Templates.
 */
public interface AllVcsesI {
  void registerManually(@NotNull AbstractVcs vcs);
  void unregisterManually(@NotNull AbstractVcs vcs);
  AbstractVcs getByName(String name);
  AbstractVcs[] getAll();
  boolean isEmpty();
}
