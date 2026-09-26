fun test(target: JavaCollectionTarget, recipients: List<String>?, quotas: Map<String, Int>?, otherRecipients: List<String>?) {
    target.updateRecipients(<caret>)
}

// EXIST:  { "itemText": "recipients, quotas" }
