package com.maddyhome.idea.copyright.util;

/*
 * Copyright - Copyright notice updater for IDEA
 * Copyright (C) 2004-2005 Rick Maddy. All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

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

    private Calendar calendar;
}