package com.intellij.vcs.log;

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
public interface VcsLogRefSorter {

  /**
   * Sorts the given references.
   */
  List<VcsRef> sort(Collection<VcsRef> refs);

}
