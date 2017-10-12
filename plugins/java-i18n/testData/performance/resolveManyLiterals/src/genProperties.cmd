@echo off
del *properties

echo class PropRef { >PropRef.java
for /L %%f in (1,1,100) do for /L %%i in (1,1,10) do call handleProp %%f %%i >>PropRef.java

echo } >>PropRef.java