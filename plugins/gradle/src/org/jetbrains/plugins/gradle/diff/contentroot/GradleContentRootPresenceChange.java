package org.jetbrains.plugins.gradle.diff.contentroot;

import com.intellij.openapi.roots.ContentEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.diff.GradleAbstractEntityPresenceChange;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChangeVisitor;
import org.jetbrains.plugins.gradle.model.gradle.GradleContentRoot;
import org.jetbrains.plugins.gradle.model.id.GradleContentRootId;
import org.jetbrains.plugins.gradle.model.id.GradleEntityIdMapper;
import org.jetbrains.plugins.gradle.util.GradleBundle;

/**
 * @author Denis Zhdanov
 * @since 2/22/12 5:12 PM
 */
public class GradleContentRootPresenceChange extends GradleAbstractEntityPresenceChange<GradleContentRootId> {

  public GradleContentRootPresenceChange(@Nullable GradleContentRoot gradleEntity, @Nullable ContentEntry intellijEntity)
    throws IllegalArgumentException
  {
    super(GradleBundle.message("gradle.import.structure.tree.node.content.root"), of(gradleEntity), of(intellijEntity));
  }

  @Nullable
  private static GradleContentRootId of(@Nullable Object contentRoot) {
    if (contentRoot == null) {
      return null;
    }
    return GradleEntityIdMapper.mapEntityToId(contentRoot);
  }

  @Override
  public void invite(@NotNull GradleProjectStructureChangeVisitor visitor) {
    visitor.visit(this); 
  }
}
