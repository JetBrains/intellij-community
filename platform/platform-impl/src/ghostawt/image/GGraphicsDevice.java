// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package ghostawt.image;

import java.awt.*;
import java.awt.image.ColorModel;

public class GGraphicsDevice extends GraphicsDevice {
    private String idString;
    private GGraphicsConfiguration defaultConfiguration;
    private ColorModel colorModel;
    
    
    public GGraphicsDevice() {
        idString = "Display0";
    }
    
    @Override
    public int getType() {
        return TYPE_RASTER_SCREEN;
    }

    @Override
    public String getIDstring() {
        return idString;
    }

    @Override
    public GraphicsConfiguration[] getConfigurations() {
        return new GraphicsConfiguration[]{getDefaultConfiguration()};
    }

    @Override
    public GraphicsConfiguration getDefaultConfiguration() {
        if(defaultConfiguration == null) {
            defaultConfiguration = new GGraphicsConfiguration(this);
        }
        return defaultConfiguration;
    }

    public ColorModel getColorModel() {
        if (colorModel == null)  {
            colorModel = ColorModel.getRGBdefault();
        }
        return colorModel;
    }

}
