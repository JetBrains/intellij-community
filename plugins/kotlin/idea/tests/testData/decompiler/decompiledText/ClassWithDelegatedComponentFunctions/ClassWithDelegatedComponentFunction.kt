package test

import dependency.ComponentInterface

class ClassWithDelegatedComponentFunctions(delegate: ComponentInterface): ComponentInterface by delegate