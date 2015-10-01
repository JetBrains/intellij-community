package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class MalformedFormatStringInspectionTest extends LightInspectionTestCase {

  public void testMalformedFormatString() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final MalformedFormatStringInspection inspection = new MalformedFormatStringInspection();
    inspection.classNames.add("com.siyeh.igtest.bugs.malformed_format_string.MalformedFormatString.SomeOtherLogger");
    inspection.methodNames.add("d");
    return inspection;
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package java.time.temporal;\n" +
      "public interface TemporalAccessor {\n" +
      "    boolean isSupported(TemporalField field);\n" +
      "    default ValueRange range(TemporalField field) {\n" +
      "        return null;\n" +
      "    }\n" +
      "    default int get(TemporalField field) {\n" +
      "        return 0;\n" +
      "    }\n" +
      "    long getLong(TemporalField field);\n" +
      "    default <R> R query(TemporalQuery<R> query) {\n" +
      "        return null;\n" +
      "    }\n" +
      "}",

      "package java.time.temporal;\n" +
      "public interface Temporal extends TemporalAccessor {\n" +
      "    boolean isSupported(TemporalUnit unit);\n" +
      "    default Temporal with(TemporalAdjuster adjuster) {\n" +
      "        return null;\n" +
      "    }\n" +
      "    Temporal with(TemporalField field, long newValue);\n" +
      "    default Temporal plus(TemporalAmount amount) {\n" +
      "        return null;\n" +
      "    }\n" +
      "    Temporal plus(long amountToAdd, TemporalUnit unit);\n" +
      "    default Temporal minus(TemporalAmount amount) {\n" +
      "        return null;\n" +
      "    }\n" +
      "    default Temporal minus(long amountToSubtract, TemporalUnit unit) {\n" +
      "        return null;\n" +
      "    }\n" +
      "    long until(Temporal endExclusive, TemporalUnit unit);\n" +
      "}",

      "package java.time;\n" +
      "import java.time.temporal.Temporal;\n" +
      "public abstract class ZonedDateTime implements Temporal {\n" +
      "    public static ZonedDateTime now() {\n" +
      "        return null;\n" +
      "    }\n" +
      "}"
    };
  }
}