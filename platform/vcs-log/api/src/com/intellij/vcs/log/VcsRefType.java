package com.intellij.vcs.log;

import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author Kirill Likhodedov
 */
public interface VcsRefType {

  /**
   * <p>Tells if this reference type should be considered a branch.</p>
   * <p>
   * <p>Although there are different ref types across different VCSs, generally they can be divided into development branches producing
   * separate branches in the graph, and tags which are just handy labels to specific repository states.
   * This difference is not clear enough for Git branches or Mercurial bookmarks, which internally have no differences with tags,
   * but they are still considered as branches due to their purpose, rather than to their internal structure.</p>
   * <p>
   * <p>Although this is implementation specific and may change, here are some examples of the difference between branches and not-branches
   * considering the VCS Log:
   * <ul>
   * <li>branch references stop collapsing of long linear branches, while tags don't.</li>
   * <li>branches try to keep their color through time, while tags don't affect colors of branches.</li>
   * </ul></p>
   */
  boolean isBranch();

  /**
   * Returns the background color which should be used to paint a {@link VcsRef reference label} of this type.
   * TODO maybe this is not the right place for color
   */
  @NotNull
  Color getBackgroundColor();
}
