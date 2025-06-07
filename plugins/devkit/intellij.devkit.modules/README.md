# IntelliJ DevKit Modules

This module provides a tool window for IntelliJ Platform projects that displays all JPS modules in the project, their source roots, and dependencies.

## Overview

The Modules tool window allows developers working on IntelliJ Platform projects to:
- View all JPS modules in the project
- Explore module source roots (Java source, resources, test sources, test resources)
- View module dependencies (both module and library dependencies)
- Examine plugin.xml and module XML files
- Filter modules by name
- Navigate to source files by double-clicking on them
- Find usages of modules, source roots, and dependencies

## Components

### Tool Window

The module registers a tool window with ID "Modules" that is anchored to the right side of the IDE. The tool window is created by the `ModulesToolWindowFactory` class and displays a `ModulesPanel`.

### Services

The module provides four services:

1. **JpsModulesService**
   - Interface: `JpsModulesService`
   - Implementation: `JpsModulesServiceImpl`
   - Purpose: Loads and provides JPS modules data
   - Key methods:
     - `getAllModules()`: Gets all JPS modules for the project, sorted by name
     - `findXmlFilesInModule(module)`: Finds plugin.xml and module XML files in a module

2. **XmlParserService**
   - Interface: `XmlParserService`
   - Implementation: `XmlParserServiceImpl`
   - Purpose: Parses XML files (plugin.xml and module XML files)
   - Key methods:
     - `parsePluginXml(file)`: Parses a plugin.xml file
     - `parseModuleXml(file)`: Parses a module XML file

3. **ModulesTreeModelService**
   - Interface: `ModulesTreeModelService`
   - Implementation: `ModulesTreeModelServiceImpl`
   - Purpose: Manages the modules tree model
   - Key methods:
     - `updateModulesList(prefix)`: Updates the list of modules in the tree based on the current filter

4. **ModulesTreeReferenceProvider**
   - Interface: None (direct implementation)
   - Implementation: `ModulesTreeReferenceProvider`
   - Purpose: Provides references to module tree nodes for find usages functionality
   - Key methods:
     - `findReferences(moduleNode, searchScope)`: Finds references to the specified module node
     - `associateElementWithNode(element, moduleNode)`: Associates a PsiElement with a module node

### UI Components

The main UI component is the `ModulesPanel` class, which:
- Displays a tree of modules, source roots, and dependencies
- Provides a filter for searching modules
- Handles navigation to source files
- Renders different types of nodes with appropriate icons and text

### Utilities

The module includes utility functions in `JpsModelRoutines.kt` for loading the JPS model from a project path.

## File Structure

```
intellij.devkit.modules/
├── resources/
│   └── intellij.devkit.modules.xml  # Plugin configuration file
└── src/
    └── toolwindow/
        ├── JpsModelRoutines.kt      # Utilities for loading JPS model
        ├── JpsModulesService.kt     # Service interface for JPS modules
        ├── JpsModulesServiceImpl.kt # Implementation of JpsModulesService
        ├── ModulesFindUsagesAction.kt # Action for finding usages of modules
        ├── ModulesPanel.kt          # UI panel for displaying modules
        ├── ModulesToolWindowFactory.kt # Factory for creating the tool window
        ├── ModulesTreeFindUsagesHandlerFactory.kt # Factory for creating find usages handlers
        ├── ModulesTreeModel.kt      # Model for the modules tree
        ├── ModulesTreeModelService.kt # Service interface for the tree model
        ├── ModulesTreeModelServiceImpl.kt # Implementation of the tree model service
        ├── ModulesTreeNavigator.kt  # Navigator for the modules tree
        ├── ModulesTreeReferenceProvider.kt # Provider for finding references to modules
        └── XmlParserService.kt      # Service for parsing XML files
```

## Usage

The Modules tool window is automatically available in IntelliJ Platform projects. To use it:

1. Open an IntelliJ Platform project
2. Click on the "Modules" tool window button on the right side of the IDE
3. Browse the modules, source roots, and dependencies
4. Use the filter field to search for specific modules
5. Double-click on source roots to navigate to the corresponding files
6. Right-click on a module, source root, or dependency to access the context menu
7. Select "Find Usages" from the context menu or press Alt+F7 to find usages of the selected item

## Dependencies

This module depends on:
- com.intellij.modules.platform
- JPS model API for working with module data

## Notes

- The tool window is only available for IntelliJ Platform projects (checked via `IntelliJProjectUtil.isIntelliJPlatformProject`)
- When loading the JPS model, the module sets up path variables, including the MAVEN_REPOSITORY variable
- For externally stored project configurations (not under .idea directory), make sure the System property "external.project.config" is set to point to .../system/projects/your_project/external_build_system

## Development

This module is developed by Junie. Contributions are welcome!
