package com.siyeh.ig.telemetry;

import javax.swing.*;
import java.net.URL;

class IconHelper{
    private IconHelper(){
        super();
    }

    public static ImageIcon getIcon(String location){
        final Class thisClass = IconHelper.class;
        final URL resource = thisClass.getResource(location);
        return new ImageIcon(resource);
    }
}
