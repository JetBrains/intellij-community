/*
 * User: anna
 * Date: 13-May-2010
 */
package com.intellij.appengine.server.run;

import com.intellij.javaee.run.configuration.J2EEConfigurationProducer;

public class AppEngineServerConfigurationProducer extends J2EEConfigurationProducer{
  public AppEngineServerConfigurationProducer() {
    super(AppEngineServerConfigurationType.getInstance());
  }
}
