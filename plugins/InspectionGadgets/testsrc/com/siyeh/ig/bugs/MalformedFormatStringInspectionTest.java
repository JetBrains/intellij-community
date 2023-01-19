/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class MalformedFormatStringInspectionTest extends LightJavaInspectionTestCase {

  public void testMalformedFormatString() {
    doTest();
  }
  public void testPrintFormatAnnotation() {
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
      "package java.util;" +
      "public interface Formattable {" +
      "  void formatTo(Formatter formatter, int flags, int width, int precision);" +
      "}",

      """
package java.time.temporal;
public interface TemporalAccessor {
    boolean isSupported(TemporalField field);
    default ValueRange range(TemporalField field) {
        return null;
    }
    default int get(TemporalField field) {
        return 0;
    }
    long getLong(TemporalField field);
    default <R> R query(TemporalQuery<R> query) {
        return null;
    }
}""",

      """
package java.time.temporal;
public interface Temporal extends TemporalAccessor {
    boolean isSupported(TemporalUnit unit);
    default Temporal with(TemporalAdjuster adjuster) {
        return null;
    }
    Temporal with(TemporalField field, long newValue);
    default Temporal plus(TemporalAmount amount) {
        return null;
    }
    Temporal plus(long amountToAdd, TemporalUnit unit);
    default Temporal minus(TemporalAmount amount) {
        return null;
    }
    default Temporal minus(long amountToSubtract, TemporalUnit unit) {
        return null;
    }
    long until(Temporal endExclusive, TemporalUnit unit);
}""",

      """
package java.time;
import java.time.temporal.Temporal;
public abstract class ZonedDateTime implements Temporal {
    public static ZonedDateTime now() {
        return null;
    }
}""",

      """
package java.time;
import java.time.temporal.Temporal;
public abstract class LocalDateTime implements Temporal {
    public static LocalDateTime now() {
        return null;
    }
}""",

      """
package java.time;
import java.time.temporal.Temporal;
public abstract class LocalDate implements Temporal {
    public static LocalDate now() {
        return null;
    }
}""",

      """
package java.time;
import java.time.temporal.Temporal;
public abstract class LocalTime implements Temporal {
    public static LocalTime now() {
        return null;
    }
}""",

      """
package java.time;
import java.time.temporal.Temporal;
public abstract class OffsetDateTime implements Temporal {
    public static OffsetDateTime now() {
        return null;
    }
}""",

      """
package java.time;
import java.time.temporal.Temporal;
public abstract class OffsetTime implements Temporal {
    public static OffsetTime now() {
        return null;
    }
}"""
    };
  }
}