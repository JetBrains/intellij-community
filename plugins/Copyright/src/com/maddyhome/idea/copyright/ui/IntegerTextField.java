package com.maddyhome.idea.copyright.ui;

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

import java.text.Format;
import java.text.NumberFormat;
import java.text.ParseException;
import javax.swing.JFormattedTextField;

public class IntegerTextField extends JFormattedTextField
{
    public IntegerTextField()
    {
    }

    public IntegerTextField(Object object)
    {
        super(object);
    }

    public IntegerTextField(Format format)
    {
        super(format);
    }

    public IntegerTextField(AbstractFormatter abstractFormatter)
    {
        super(abstractFormatter);
    }

    public IntegerTextField(AbstractFormatterFactory abstractFormatterFactory)
    {
        super(abstractFormatterFactory);
    }

    public IntegerTextField(AbstractFormatterFactory abstractFormatterFactory, Object object)
    {
        super(abstractFormatterFactory, object);
    }

    public void setRange(int min, int max)
    {
        setFormatterFactory(new IntegerFormatterFactory(min, max));
    }

    protected static class IntegerFormatterFactory extends JFormattedTextField.AbstractFormatterFactory
    {
        public IntegerFormatterFactory(int min, int max)
        {
            this.min = min;
            this.max = max;
        }

        public AbstractFormatter getFormatter(JFormattedTextField jFormattedTextField)
        {
            return new IntegerFormatter(min, max);
        }

        private int min;
        private int max;
    }

    protected static class IntegerFormatter extends JFormattedTextField.AbstractFormatter
    {
        public IntegerFormatter(int min, int max)
        {
            this.min = min;
            this.max = max;
        }

        public Object stringToValue(String string) throws ParseException
        {
            Number res = formatter.parse(string);
            if (res.intValue() < min || res.intValue() > max)
            {
                throw new ParseException("Out of range", 0);
            }

            return res;
        }

        public String valueToString(Object object) throws ParseException
        {
            if (object == null)
            {
                return null;
            }

            return object.toString();
        }

        private int min;
        private int max;
        private NumberFormat formatter = NumberFormat.getIntegerInstance();
    }
}
