package com.intellij.vcs.log;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * <p>Sorts {@link VcsRef references} of branches and tags according to their type and other means.</p>
 *
 * <p><b>Note:</b> it is intended to sort references from a single root. It is possible to pass references from different roots,
 *    but the result would be as if it were refs from the same root.</p>
 *
 * @author Kirill Likhodedov
 */
public interface VcsLogRefManager {

  /**
   * Sorts the given references.
   * TODO better provide compareTo
   */
  @NotNull
  List<VcsRef> sort(Collection<VcsRef> refs);

  /**
   * <p>Groups VCS references to show them on the branches panel.</p>
   * <p>Groups containing only one element will be displayed as a single ref. Others will provide a popup menu.</p>
   * <p>Groups must be pre-sorted in the order which they are to be painted on the panel.</p>
   */
  @NotNull
  List<RefGroup> group(Collection<VcsRef> refs);

}
