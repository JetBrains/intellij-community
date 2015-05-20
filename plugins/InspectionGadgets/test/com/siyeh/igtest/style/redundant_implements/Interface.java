package com.siyeh.igtest.style.redundant_implements;

import java.util.Collection;
import java.util.List;

interface RedundantImplementsInspection3 extends List, <warning descr="Redundant interface declaration 'Collection'">Collection</warning>{
}
