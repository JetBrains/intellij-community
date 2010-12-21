package com.intellij.coverage;

import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.javaee.run.configuration.CommonModel;
import org.jetbrains.annotations.Nullable;

/**
 * User: anna
 * Date: 12/21/10
 */
public class J2EECoverageEngine extends JavaCoverageEngine {
  @Override
  public boolean isApplicableTo(@Nullable RunConfigurationBase conf) {
    return conf instanceof CommonModel && ((CommonModel)conf).isLocal();
  }
}
