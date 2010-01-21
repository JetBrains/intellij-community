package com.intellij.coverage;

import com.intellij.openapi.project.Project;
import com.intellij.ultimate.PluginVerifier;
import com.intellij.ultimate.UltimateVerifier;

/**
 * @author Roman.Chernyatchik
 */
public class CoveragePluginDataManagerImpl extends CoverageDataManagerImpl {
  public CoveragePluginDataManagerImpl(final Project project, UltimateVerifier verifier) {
    super(project);
    PluginVerifier.verifyUltimatePlugin(verifier);
  }
}
