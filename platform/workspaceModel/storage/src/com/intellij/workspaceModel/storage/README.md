# Workspace model specification

## Hard references

#### TODO questions and improvements

**Optional parent**  
Do we need entities with an optional parent?
If we decide that all children should have a connection to an entity with a PersistentId,
this option should not be presented.

Example of correct connection with an optional parent: Facet to Facet (underlying facets)

**Finish all possible types of the references**  
At the moment supported and specified only references that are currently used by intellij project model.
We should implement and specify all remaining types of references.


#### One-To-Many

- One-To-Many
  - One-To-Many with optional parent
  - One-To-Many with mandatory parent

#### One-To-Abstract-Many

This reference has a special behaviour. During addDIff it doesn't try to merge added and remove children,
but just replaces all children.
This behaviour should be updated if you want a version of this reference that merges children

- One-To-Abstract-Many  
  - One-To-Abstract-Many with optional parent

Almost the same as `One-To-Many`, but allowing to reference to children of abstract classes

#### One-To-One
In `one-to-one` connections **child is always optional for the parent**.  
This is the restriction of the current specification. See the explanation below.

- One-To-One  
  - One-To-One with mandatory parent

One-To-One connection has at the moment only mandatory parent. Optional parent may be supported if needed.

#### One-To-Abstract-One
In `one-to-abstract-one` connections **child is always optional for the parent**.  
This is the restriction of the current specification. See the explanation below.

- One-To-Abstract-One  
  - One-To-Abstract-One with optional parent

#### One-To-One missing mandatory child

It's not allowed to have a mandatory child reference in `One-To-One` connections.
This is made because of the following reasons:
- Since the cascade delete doesn't work from child to parent,
  a mandatory child reference will constrain child entity deletion.
  The deletion of mandatory child cannot be verified by the compiler, so this type of reference will
  introduce additional exceptions in the project model.
- The inability to remove child reference may introduce complicated conditional behaviour changes.  
  E.g. let's say we have a `ModuleEntity` and one-to-many connected `ContentRootEntity`. So, after the removing
  the `ModuleEntity` we expect that all `ContentRootEntity`-ies would be removed as well.
  However, if some external plugin adds a parent for the `ContentRootEntity` with one-to-one connection,
  the removing of `ContentRootEntity`-ies is no more possible. So, the behaviour of the workspace model and
  the IDE changes depending on persistence of this external plugin.

  _This issue makes the behaviour of workspace model more unpredictable for the users._
  

