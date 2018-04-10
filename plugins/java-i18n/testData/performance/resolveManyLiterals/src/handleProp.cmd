@echo off

set propName=prop.file.%1.number.%2
echo %propName%=%propName% >>file%1.properties
echo String s%1_%2 = "%propName%";