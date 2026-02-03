package com.intellij.coverage;

import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

public class CoverageRunnerData implements RunnerSettings{
  @Override
  public void readExternal(Element element) throws InvalidDataException {}

  @Override
  public void writeExternal(Element element) throws WriteExternalException {}
}
