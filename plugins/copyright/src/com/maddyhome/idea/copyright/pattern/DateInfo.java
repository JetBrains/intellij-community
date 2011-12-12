/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.maddyhome.idea.copyright.pattern;

import java.text.SimpleDateFormat;
import java.util.Calendar;

public class DateInfo
{
    public DateInfo()
    {
        calendar = Calendar.getInstance();
    }

    public DateInfo(long time)
    {
        calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
    }

    public int getYear()
    {
        return calendar.get(Calendar.YEAR);
    }

    public int getMonth()
    {
        return calendar.get(Calendar.MONTH) + 1;
    }

    public int getDay()
    {
        return calendar.get(Calendar.DAY_OF_MONTH);
    }

    public int getHour()
    {
        return calendar.get(Calendar.HOUR);
    }

    public int getMinute()
    {
        return calendar.get(Calendar.MINUTE);
    }

    public int getSecond()
    {
        return calendar.get(Calendar.SECOND);
    }

    public String format(String format)
    {
        SimpleDateFormat sdf = new SimpleDateFormat(format);

        return sdf.format(calendar.getTime());
    }

    private final Calendar calendar;

    @Override
    public String toString() {
      return new SimpleDateFormat().format(calendar.getTime());
    }
}