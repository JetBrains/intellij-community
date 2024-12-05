package org.jetbrains.intellij.plugins.journey;

import com.intellij.openapi.util.Key;
import org.jetbrains.intellij.plugins.journey.diagram.JourneyDiagramDataModel;

public final class JourneyDataKeys {
  public static final Key<JourneyDiagramDataModel> JOURNEY_DIAGRAM_DATA_MODEL = Key.create("JourneyDiagramDataModel");

  public static final String JOURNEY_SLOW_OPERATIONS = "journey";

}
