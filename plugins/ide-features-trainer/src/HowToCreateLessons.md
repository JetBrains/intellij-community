###Paths

We should be careful with paths of all lessons and _xml files_ that we are using to hold lessons data. 

If you need to add support for a custom language you should:
1. Add extension for `training.lang.LangSupport`
2. Add your _LearnProject_ realisation and put it in `res/learnProjects/$langName$`
3. Specify _LearnProject_ name in `LangSupport`

How is plugin looking for a modules: `res/data/modules.xml`. This file contains a relative path from modules 
dir to a _module xml_. 
 
```xml
<modules version="0.3">
       <module name="modules/EditorBasics.xml"/>
       <module name="modules/Completions.xml"/>
       <module name="modules/Refactorings.xml"/>
       <module name="modules/CodeAssistance.xml"/>
       <module name="modules/Navigation.xml"/>
       <!--<module name="modules/Loops.xml"/>-->
   </modules>
   ```
   
_Module xml_ has a list of lessons with a relative path to them: 
```xml
<?xml version="1.0" encoding="UTF-8"?>
<module name="Refactorings" lessonsPath="Refactorings/" version="0.3" id="refactorings" fileType="PROJECT" description="Rename, extract variable and method and other refactorings">
  <lesson filename="01.Rename.xml"/>
  <lesson filename="02.Extract Variable.xml" />
  <lesson filename="03.Extract Method.xml"/>
  <lesson filename="04.RefactoringBasics.xml"/>
</module>
```

So lessons for _Refactorings_ module are located in `res/data/modules/java/Refactorings/01.Rename.xml`. 

