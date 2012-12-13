package com.siyeh.igtest.security.cloneable_class_in_secure_context;

class CloneableClassInSecureContext implements Cloneable {
}
interface EventListener<E extends Cloneable> {
}