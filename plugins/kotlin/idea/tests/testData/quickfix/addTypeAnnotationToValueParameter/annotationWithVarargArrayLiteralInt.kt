// "Add type 'Int' to parameter 'value'" "true"
// LANGUAGE_VERSION: 1.7

annotation class CollectionDefault(vararg val value = [1, 2]<caret>)