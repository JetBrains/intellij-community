// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.otExporters;

import static org.junit.Assert.*;

import org.junit.*;

public class CsvMetricsExporterTest {

  @Test
  public void plotterHtmlIsAccessibleFromCsvMetricsExporterClass() throws Exception {
    assertNotNull(
      "Resource [" + CsvMetricsExporter.HTML_PLOTTER_NAME + "] must be accessible with CsvMetricsExporter.class.getResource()",
      CsvMetricsExporter.class.getResource(CsvMetricsExporter.HTML_PLOTTER_NAME)
    );
  }
}