package org.jetbrains.intellij.plugins.journey;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.intellij.plugins.journey.diagram.JourneyDiagramDataModel;

public final class JourneyDataKeys {
  // TODO move the key here. Not it is in Project for hacks in platform.
  public static final Key<JourneyDiagramDataModel> JOURNEY_DIAGRAM_DATA_MODEL = (Key<JourneyDiagramDataModel>) (Key) Project.JOURNEY_DIAGRAM_DATA_MODEL;
}
