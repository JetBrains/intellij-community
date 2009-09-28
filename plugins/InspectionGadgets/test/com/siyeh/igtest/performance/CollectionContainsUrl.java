package com.siyeh.igtest.performance;

import java.net.URL;
import java.net.MalformedURLException;
import java.util.List;
import java.util.ArrayList;

public class CollectionContainsUrl {
    
    static final List<URL> list = new ArrayList();
    static final List urlList = new ArrayList();

    static {
        try {
            list.add(new URL(null));
            list.add(new URL(null));
            urlList.add(new URL(null));
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }
}
