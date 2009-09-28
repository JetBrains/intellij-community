package com.siyeh.igtest.verbose;

import java.util.ArrayList;
import java.util.List;
import java.io.Serializable;

public class RedundantImplementsInspection extends ArrayList implements List, Serializable{
}
