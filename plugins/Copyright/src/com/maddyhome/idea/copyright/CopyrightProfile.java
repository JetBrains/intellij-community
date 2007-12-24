package com.maddyhome.idea.copyright;

import com.intellij.profile.ProfileEx;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.maddyhome.idea.copyright.options.Options;
import org.jdom.Element;

public class CopyrightProfile extends ProfileEx {
    public Options myOptions = new Options();

    //read external
    public CopyrightProfile() {
        super("");
    }

    public CopyrightProfile(String profileName) {
        super(profileName);
    }

    public Options getOptions() {
        return myOptions;
    }

    public void setOptions(Options options) {
        myOptions = options;
    }
}
