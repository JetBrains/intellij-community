package org.jetbrains.android.compiler.artifact;

import com.intellij.packaging.artifacts.ArtifactProperties;
import com.intellij.packaging.artifacts.ArtifactPropertiesProvider;
import com.intellij.packaging.artifacts.ArtifactType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidArtifactPropertiesProvider extends ArtifactPropertiesProvider {
  protected AndroidArtifactPropertiesProvider() {
    super("android-properties");
  }

  @Override
  public boolean isAvailableFor(@NotNull ArtifactType type) {
    return type instanceof AndroidApplicationArtifactType;
  }

  @NotNull
  @Override
  public ArtifactProperties<?> createProperties(@NotNull ArtifactType artifactType) {
    return new AndroidApplicationArtifactProperties();
  }

  public static AndroidArtifactPropertiesProvider getInstance() {
    return EP_NAME.findExtension(AndroidArtifactPropertiesProvider.class);
  }
}
