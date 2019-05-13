package com.siyeh.igtest.style.redundant_implements;

import java.util.ArrayList;
import java.util.List;
import java.io.Serializable;

class RedundantImplementsInspection extends ArrayList implements <warning descr="Redundant interface declaration 'List'">List</warning>, <warning descr="Redundant interface declaration 'Serializable'">Serializable</warning>{
}
