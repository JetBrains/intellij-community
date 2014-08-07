package com.intellij.vcs.log;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Comparator;
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
   * Return the comparator which compares two given references in terms of their "importance",
   * which is later is used in the log to order branches and branch labels.
   * <p><ul>
   * <li>Negative value is returned if first reference is <b>more</b> important than the second (i.e. it will be at the left in the log).
   * <li>Positive value is returned if first reference is <b>less</b> important than the second (i.e. it will be at the right in the log).
   * <li>Zero is returned if referenced are considered equally important.
   * </ul>
   */
  @NotNull
  Comparator<VcsRef> getComparator();

  /**
   * <p>Groups VCS references to show them on the branches panel.</p>
   * <p>Groups containing only one element will be displayed as a single ref. Others will provide a popup menu.</p>
   * <p>Groups must be pre-sorted in the order which they are to be painted on the panel.</p>
   */
  @NotNull
  List<RefGroup> group(Collection<VcsRef> refs);

}
