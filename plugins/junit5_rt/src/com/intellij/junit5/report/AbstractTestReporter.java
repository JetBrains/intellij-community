// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit5.report;

import com.intellij.rt.execution.junit.MapSerializerUtil;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.launcher.TestIdentifier;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public abstract class AbstractTestReporter implements TeamCityTestReporter {
  protected final TestIdentifier identifier;
  protected final ExecutionState state;

  protected AbstractTestReporter(TestIdentifier identifier, ExecutionState state) {
    this.identifier = identifier;
    this.state = state;
  }

  @Override
  public List<String> output(ReportEntry entry) {
    Map<String, String> attrs = attributes(ReportedField.ID, ReportedField.NAME, ReportedField.NODE_ID, ReportedField.PARENT_NODE_ID);

    StringBuilder builder = new StringBuilder();
    builder.append("timestamp = ").append(entry.getTimestamp());
    entry.getKeyValuePairs().forEach((key, value) -> builder.append(", ").append(key).append(" = ").append(value));
    builder.append("\n");

    attrs.put("out", builder.toString());
    return Collections.singletonList(MapSerializerUtil.asString(MapSerializerUtil.TEST_STD_OUT, attrs));
  }

  protected String id() {
    return identifier.getUniqueId() + state.suffix();
  }

  protected Optional<SuiteReporter> getParent() {
    //noinspection SimplifyOptionalCallChains
    return state.plan().getParent(identifier)
      .map(i -> new SuiteReporter(i, state))
      .map(r -> r.isSkipped() ? r.getParent() : Optional.of(r))
      .orElse(Optional.empty());
  }

  protected Map<String, String> attributes(ReportedField... fields) {
    Map<String, String> attrs = new LinkedHashMap<>(fields.length * 2);
    LocationInfo location = locationInfo();

    for (ReportedField field : fields) {
      String value = null;
      switch (field) {
        case ID:
        case NODE_ID:
          value = id();
          break;
        case NAME:
          value = name();
          break;
        case PARENT_NODE_ID:
          value = getParent().map(AbstractTestReporter::id).orElse("0");
          break;
        case HINT:
          value = location.locationHint();
          break;
        case METAINFO:
          value = location.metainfo();
          break;
      }
      if (value != null) {
        attrs.put(field.getId(), value);
      }
    }
    return attrs;
  }

  protected String name() {
    return identifier.getDisplayName();
  }

  private LocationInfo locationInfo() {
    if (state.plan() == null) return LocationInfo.EMPTY;
    TestIdentifier parent = state.plan().getParent(identifier).orElse(null);
    return LocationInfo.compute(identifier, parent);
  }

  protected static String getTrace(Throwable ex) {
    StringWriter writer = new StringWriter();
    ex.printStackTrace(new PrintWriter(writer));
    return writer.toString();
  }

  protected enum ReportedField {
    ID("id"),
    NAME("name"),
    NODE_ID("nodeId"),
    PARENT_NODE_ID("parentNodeId"),
    HINT("locationHint"),
    METAINFO("metainfo");

    private final String myId;

    ReportedField(String id) {
      myId = id;
    }

    public String getId() {
      return myId;
    }
  }
}