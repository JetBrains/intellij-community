// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text;

import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateFormatAdapterTest {
  private static final LocalDate DATE = LocalDate.of(2024, 1, 2);
  private static final LocalDateTime DATE_TIME = DATE.atTime(3, 4);

  @Test void customDateFormat() throws ParseException {
    var formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    assertFormat(formatter, "02.01.2024", DATE.atStartOfDay());
  }

  @Test void customDateTimeFormat() throws ParseException {
    var formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    assertFormat(formatter, "02.01.2024 03:04", DATE_TIME);
  }

  private static void assertFormat(DateTimeFormatter formatter, String expectedText, LocalDateTime expectedParsed) throws ParseException {
    var adapter = new DateFormatAdapter(formatter);
    var date = toDate(DATE_TIME);
    assertThat(adapter.format(date)).isEqualTo(expectedText);
    assertThat(adapter.format(date.getTime())).isEqualTo(expectedText);
    assertThat(adapter.parse(expectedText)).isEqualTo(toDate(expectedParsed));
    assertThatThrownBy(() -> adapter.parse("not a date")).isInstanceOf(ParseException.class);
  }

  private static Date toDate(LocalDateTime value) {
    return Date.from(value.atZone(ZoneId.systemDefault()).toInstant());
  }
}
