/*
 * Copyright 2004-2005 Alexey Efimov
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
package org.intellij.images.options.impl;

import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.JDOMExternalizer;
import org.intellij.images.options.ZoomOptions;
import org.jdom.Element;

import java.awt.*;
import java.beans.PropertyChangeSupport;

/**
 * Zoom options implementation.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
final class ZoomOptionsImpl implements ZoomOptions, JDOMExternalizable {
    private boolean wheelZooming;
    private boolean smartZooming = true;
    private int prefferedWidth = DEFAULT_PREFFERED_SIZE.width;
    private int prefferedHeight = DEFAULT_PREFFERED_SIZE.height;
    private final PropertyChangeSupport propertyChangeSupport;

    ZoomOptionsImpl(PropertyChangeSupport propertyChangeSupport) {
        this.propertyChangeSupport = propertyChangeSupport;
    }

    public boolean isWheelZooming() {
        return wheelZooming;
    }

    public boolean isSmartZooming() {
        return smartZooming;
    }

    public Dimension getPrefferedSize() {
        return new Dimension(prefferedWidth, prefferedHeight);
    }

    void setWheelZooming(boolean wheelZooming) {
        boolean oldValue = this.wheelZooming;
        if (oldValue != wheelZooming) {
            this.wheelZooming = wheelZooming;
            propertyChangeSupport.firePropertyChange(ATTR_WHEEL_ZOOMING, oldValue, this.wheelZooming);
        }
    }

    void setSmartZooming(boolean smartZooming) {
        boolean oldValue = this.smartZooming;
        if (oldValue != smartZooming) {
            this.smartZooming = smartZooming;
            propertyChangeSupport.firePropertyChange(ATTR_SMART_ZOOMING, oldValue, this.smartZooming);
        }
    }

    void setPrefferedSize(Dimension prefferedSize) {
        if (prefferedSize == null) {
            prefferedSize = DEFAULT_PREFFERED_SIZE;
        }
        setPrefferedWidth(prefferedSize.width);
        setPrefferedHeight(prefferedSize.height);
    }

    void setPrefferedWidth(int prefferedWidth) {
        int oldValue = this.prefferedWidth;
        if (oldValue != prefferedWidth) {
            this.prefferedWidth = prefferedWidth;
            propertyChangeSupport.firePropertyChange(ATTR_PREFFERED_WIDTH, oldValue, this.prefferedWidth);
        }
    }

    void setPrefferedHeight(int prefferedHeight) {
        int oldValue = this.prefferedHeight;
        if (oldValue != prefferedHeight) {
            this.prefferedHeight = prefferedHeight;
            propertyChangeSupport.firePropertyChange(ATTR_PREFFERED_HEIGHT, oldValue, this.prefferedHeight);
        }
    }

    public void inject(ZoomOptions options) {
        setWheelZooming(options.isWheelZooming());
        setSmartZooming(options.isSmartZooming());
        setPrefferedSize(options.getPrefferedSize());
    }

    public boolean setOption(String name, Object value) {
        if (ATTR_WHEEL_ZOOMING.equals(name)) {
            setWheelZooming((Boolean)value);
        } else if (ATTR_SMART_ZOOMING.equals(name)) {
            setSmartZooming((Boolean)value);
        } else if (ATTR_PREFFERED_WIDTH.equals(name)) {
            setPrefferedWidth((Integer)value);
        } else if (ATTR_PREFFERED_HEIGHT.equals(name)) {
            setPrefferedHeight((Integer)value);
        } else {
            return false;
        }
        return true;
    }

    public void readExternal(Element element) {
        setWheelZooming(JDOMExternalizer.readBoolean(element, ATTR_WHEEL_ZOOMING));
        setSmartZooming(JDOMExternalizer.readBoolean(element, ATTR_SMART_ZOOMING));
        setPrefferedWidth(JDOMExternalizer.readInteger(element, ATTR_PREFFERED_WIDTH, DEFAULT_PREFFERED_SIZE.width));
        setPrefferedHeight(JDOMExternalizer.readInteger(element, ATTR_PREFFERED_HEIGHT, DEFAULT_PREFFERED_SIZE.height));
    }

    public void writeExternal(Element element) {
        JDOMExternalizer.write(element, ATTR_WHEEL_ZOOMING, wheelZooming);
        JDOMExternalizer.write(element, ATTR_SMART_ZOOMING, smartZooming);
        JDOMExternalizer.write(element, ATTR_PREFFERED_WIDTH, prefferedWidth);
        JDOMExternalizer.write(element, ATTR_PREFFERED_HEIGHT, prefferedHeight);
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ZoomOptions)) {
            return false;
        }

        ZoomOptions otherOptions = (ZoomOptions)obj;

        Dimension prefferedSize = otherOptions.getPrefferedSize();
        return prefferedSize != null && prefferedHeight == prefferedSize.height &&
            prefferedWidth == prefferedSize.width &&
            smartZooming == otherOptions.isSmartZooming() &&
            wheelZooming == otherOptions.isWheelZooming();

    }

    public int hashCode() {
        int result;
        result = (wheelZooming ? 1 : 0);
        result = 29 * result + (smartZooming ? 1 : 0);
        result = 29 * result + prefferedWidth;
        result = 29 * result + prefferedHeight;
        return result;
    }
}
