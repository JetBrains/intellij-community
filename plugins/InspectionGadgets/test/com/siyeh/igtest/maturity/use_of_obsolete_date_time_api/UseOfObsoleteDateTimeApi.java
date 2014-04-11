package com.siyeh.igtest.maturity.use_of_obsolete_date_time_api;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

/**
 * @author Bas Leijdekkers
 */
public class UseOfObsoleteDateTimeApi {
  final <warning descr="Obsolete date-time type 'Date' used">Date</warning> date = new MyDate();
  List<<warning descr="Obsolete date-time type 'Date' used">Date</warning>> list;
  <warning descr="Obsolete date-time type 'TimeZone' used">TimeZone</warning> tz;

  <warning descr="Obsolete date-time type 'Calendar' used">Calendar</warning> m(<warning descr="Obsolete date-time type 'Date' used">Date</warning> d) {
    return null;
  }
}
class MyDate extends <warning descr="Obsolete date-time type 'Date' used">Date</warning> {}